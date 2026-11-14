package com.example

import com.example.domain.engine.LexiFillEngine
import com.example.domain.engine.WordTrieEngine
import com.example.domain.model.Coordinate
import com.example.ui.feedback.AudioPlayer
import com.example.ui.viewmodel.GameViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun wordTrieEngine_validatesWordsAndMatchesSkeletonPatterns() {
    val trie = WordTrieEngine()
    assertTrue(trie.isValidWord("PLANET"))
    assertTrue(trie.isValidWord("CROSS"))
    assertTrue(trie.isValidWord("QUARTZ"))
    assertFalse(trie.isValidWord("XYZQWR"))

    // Skeleton pattern matching with '_' blanks
    assertTrue(trie.hasPatternMatch("P_AN_T"))
    assertTrue(trie.hasPatternMatch("C_OSS"))
    val matches = trie.matchesPattern("P_ANET")
    assertTrue(matches.contains("PLANET"))
  }

  @Test
  fun lexiFillEngine_validatesDualAxisIntersectionAndCompletesLevel() {
    val engine = LexiFillEngine()
    val level1 = engine.getLevelTemplate(1)
    var state = engine.createPuzzleState(level1)

    // Coordinate (2, 1) is the intersection between Horizontal "PLANET" and Vertical "SLOPE" (expected 'L')
    val cell21 = state.cellAt(2, 1)!!
    assertTrue(cell21.isBlankTarget)
    assertTrue(cell21.isIntersection)
    assertEquals('L', cell21.expectedLetter)

    // Dropping an invalid letter ('Z') at intersection (2, 1) should fail dual-axis validation
    val wrongTile = state.rackState.tiles.first().copy(id = "wrong_z", char = 'Z')
    val invalidPreview = engine.previewTileDrop(state, wrongTile, 2, 1)
    assertFalse(invalidPreview.isDualAxisValid)

    // Dropping the matching 'L' tile from rack into (2, 1) should succeed
    val tileL = state.rackState.tiles.first { it.char == 'L' }
    val dropResult = engine.commitTileDrop(state, tileL.id, 2, 1)
    assertEquals(LexiFillEngine.OutcomeType.TILE_SNAPPED, dropResult.outcome)
    state = dropResult.updatedState
    assertEquals('L', state.cellAt(2, 1)?.activeLetter)

    // Completing the remaining blank of PLANET at (5, 1) ('E') should complete "PLANET"
    val tileE = state.rackState.tiles.first { it.char == 'E' }
    val completePlanet = engine.commitTileDrop(state, tileE.id, 5, 1)
    assertEquals(LexiFillEngine.OutcomeType.WORD_COMPLETED, completePlanet.outcome)
    assertTrue(completePlanet.completedWords.contains("PLANET"))
  }

  @Test
  fun audioPlayer_triggersClickSnapThudAndFanfareOnGameEvents() {
    val viewModel = GameViewModel()
    viewModel.audioPlayer.resetStatsForTesting()

    // 1. Tile selection triggers 'click'
    val firstTile = viewModel.puzzleState.value.rackState.tiles.first()
    viewModel.onRackTileTapped(firstTile)
    assertEquals(AudioPlayer.SoundEffect.CLICK, viewModel.audioPlayer.lastPlayedEffect)
    assertEquals("click", viewModel.puzzleState.value.lastSoundTriggered)

    // 2. Invalid placement triggers 'thud' (dropping 'C' into (2, 1) which expects 'L' for PLANET/SLOPE)
    val tileC = viewModel.puzzleState.value.rackState.tiles.first { it.char == 'C' }
    val acceptedInvalid = viewModel.onTileDropOnCoord(tileC.id, Coordinate(2, 1))
    assertFalse(acceptedInvalid)
    assertEquals(AudioPlayer.SoundEffect.THUD, viewModel.audioPlayer.lastPlayedEffect)
    assertEquals("thud", viewModel.puzzleState.value.lastSoundTriggered)

    // 3. Valid placement triggers 'snap' (dropping 'L' into (2, 1))
    val tileL = viewModel.puzzleState.value.rackState.tiles.first { it.char == 'L' }
    val acceptedValid = viewModel.onTileDropOnCoord(tileL.id, Coordinate(2, 1))
    assertTrue(acceptedValid)
    assertEquals(AudioPlayer.SoundEffect.SNAP, viewModel.audioPlayer.lastPlayedEffect)
    assertEquals("snap", viewModel.puzzleState.value.lastSoundTriggered)

    // 4. Solving all remaining blanks in the level triggers 'fanfare'
    while (!viewModel.puzzleState.value.isLevelCleared) {
      val s = viewModel.puzzleState.value
      val openBlank =
        s.cells.flatten().first { it.isBlankTarget && it.placedTile == null && !it.isLockedCompleted }
      val matchingTile =
        s.rackState.tiles.first { it.char == openBlank.expectedLetter }
      viewModel.onTileDropOnCoord(matchingTile.id, openBlank.coordinate)
    }
    assertTrue(viewModel.puzzleState.value.isLevelCleared)
    assertEquals(AudioPlayer.SoundEffect.FANFARE, viewModel.audioPlayer.lastPlayedEffect)
    assertEquals("fanfare", viewModel.puzzleState.value.lastSoundTriggered)
  }

  @Test
  fun campaignLevels_all100LevelsHaveValidIntersectionsAndAreSolvable() {
    val engine = LexiFillEngine()
    val allLevels = engine.getAllCampaignLevels(100)
    assertEquals(100, allLevels.size)

    for (template in allLevels) {
      val coordCharMap = HashMap<Coordinate, Char>()
      for (tw in template.targetWords) {
        assertTrue("Word ${tw.word} must be in Trie", engine.trie.isValidWord(tw.word))
        tw.coordinates().forEachIndexed { idx, coord ->
          val ch = tw.word[idx].uppercaseChar()
          val prev = coordCharMap[coord]
          if (prev != null) {
            assertEquals(
              "Intersection mismatch in Level ${template.levelNumber} (${template.title}) at $coord",
              prev,
              ch,
            )
          }
          coordCharMap[coord] = ch
        }
      }

      // Verify no two words overlap or touch orthogonally unless they share a valid intersection cell
      for (i in template.targetWords.indices) {
        for (j in i + 1 until template.targetWords.size) {
          val w1 = template.targetWords[i]
          val w2 = template.targetWords[j]
          val c1 = w1.coordinates().toSet()
          val c2 = w2.coordinates().toSet()
          val shared = c1.intersect(c2)
          if (w1.direction == w2.direction) {
            assertTrue(
              "Same-direction words ${w1.word} and ${w2.word} overlap in Level ${template.levelNumber}",
              shared.isEmpty(),
            )
          }
          // If two words do not intersect, none of their cells may touch orthogonally
          if (shared.isEmpty()) {
            for (p1 in c1) {
              for (p2 in c2) {
                val manhattan = kotlin.math.abs(p1.x - p2.x) + kotlin.math.abs(p1.y - p2.y)
                assertTrue(
                  "Non-intersecting words ${w1.word} and ${w2.word} touch at $p1 / $p2 in Level ${template.levelNumber}",
                  manhattan > 1,
                )
              }
            }
          }
        }
      }

      val state = engine.createPuzzleState(template)
      val openBlanks = state.cells.flatten().filter { it.isBlankTarget }
      assertTrue(
        "Level ${template.levelNumber} should have at least 5 blanks",
        openBlanks.size >= 5,
      )
      assertEquals(openBlanks.size, state.rackState.tiles.size)
    }
  }

  @Test
  fun levelLocking_requiresWinningRoundBeforeUnlockingNextLevel() {
    val viewModel = GameViewModel()
    assertEquals(1, viewModel.puzzleState.value.highestUnlockedLevel)

    // Cannot skip or load Level 2 before winning Level 1
    assertFalse(viewModel.loadLevel(2))
    assertEquals(1, viewModel.puzzleState.value.levelTemplate.levelNumber)

    // Win Level 1 by placing all blank tiles
    while (!viewModel.puzzleState.value.isLevelCleared) {
      val s = viewModel.puzzleState.value
      val openBlank =
        s.cells.flatten().first { it.isBlankTarget && it.placedTile == null && !it.isLockedCompleted }
      val matchingTile = s.rackState.tiles.first { it.char == openBlank.expectedLetter }
      viewModel.onTileDropOnCoord(matchingTile.id, openBlank.coordinate)
    }

    // Winning Level 1 unlocks Level 2, while Level 3 remains locked
    assertTrue(viewModel.puzzleState.value.isLevelCleared)
    assertEquals(2, viewModel.puzzleState.value.highestUnlockedLevel)
    assertFalse(viewModel.loadLevel(3))
    assertTrue(viewModel.loadLevel(2))
    assertEquals(2, viewModel.puzzleState.value.levelTemplate.levelNumber)
  }
}
