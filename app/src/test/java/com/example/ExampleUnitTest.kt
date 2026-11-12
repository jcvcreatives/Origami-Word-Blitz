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
}
