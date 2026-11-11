package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfileDocument(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "Origami Apprentice",
  val totalScore: Long = 0L,
  val highestWordScore: Long = 0L,
  val bestWord: String = "",
  val campaignStage: Long = 1L,
  val dailyStreak: Long = 1L,
  val paperCoins: Long = 120L,
  val equippedTileSkin: String = "washi_classic",
  val unlockedSkins: List<String> = listOf("washi_classic"),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SavedMatchDocument(
  @DocumentId val id: String = "",
  val userId: String = "",
  val mode: String = "CAMPAIGN",
  val opponentName: String = "Sensei Haiku",
  val playerScore: Long = 0L,
  val opponentScore: Long = 0L,
  val turnNumber: Long = 1L,
  val maxCombo: Long = 1L,
  val bestPlayedWord: String = "",
  val status: String = "ACTIVE",
  val boardSnapshot: String = ".".repeat(225),
  val rackSnapshot: String = "ORIGAMI",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
