package com.example

import com.example.domain.engine.LexiFillEngine
import com.example.domain.engine.WordTrieEngine
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
}
