package com.example

import com.example.domain.engine.BoardEvaluator
import com.example.domain.engine.WordTrieEngine
import com.example.domain.model.Coordinate
import com.example.domain.model.GameCatalog
import com.example.domain.model.LexiBoardState
import com.example.domain.model.RogueRelic
import com.example.domain.model.Tile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun wordTrieEngine_validatesWordsAndFindsAnagrams() {
    val trie = WordTrieEngine()
    assertTrue(trie.isValidWord("ORIGAMI"))
    assertTrue(trie.isValidWord("CRANE"))
    assertTrue(trie.isValidWord("QI"))
    assertFalse(trie.isValidWord("XYZQWR"))

    trie.insert("TSURU")
    assertTrue(trie.isValidWord("TSURU"))

    val anagrams = trie.findAnagrams("CRANE".toList(), minLength = 3)
    assertTrue(anagrams.contains("CRANE"))
  }

  @Test
  fun boardEvaluator_scoresFirstWordAcrossCenterStarWithDoubleWordMultiplier() {
    val trie = WordTrieEngine()
    val evaluator = BoardEvaluator(trie)
    val baseBoard = LexiBoardState(squares = GameCatalog.createStandardBoard())

    // Place "FOLD" horizontally across Center Star (7, 7) -> (6,7)=F, (7,7)=O, (8,7)=L, (9,7)=D
    val pending =
      mapOf(
        Coordinate(6, 7) to Tile("t1", 'F', 4),
        Coordinate(7, 7) to Tile("t2", 'O', 1),
        Coordinate(8, 7) to Tile("t3", 'L', 1),
        Coordinate(9, 7) to Tile("t4", 'D', 2),
      )

    val preview =
      evaluator.evaluatePendingMove(
        board = baseBoard.copy(pendingPlacements = pending),
        activeRelics = emptyList(),
        comboMultiplier = 1.0f,
        turnTimeRemainingSec = 30,
      )

    assertTrue(preview.isValidPosition)
    assertTrue(preview.allWordsValid)
    // (4 + 1 + 1 + 2) * 2 (Center Star DW) = 16
    assertTrue(preview.totalTurnScore == 16)
  }
}
