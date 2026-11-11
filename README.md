# LexiFill: Crossword Skeleton

**LexiFill: Crossword Skeleton** is an Android puzzle board game built with **100% Kotlin & Jetpack Compose** (hybrid of Scrabble multiplier tiles and Kriss-Kross Fill-In puzzles).

## Core Architecture & Features
- **MVVM + Clean Architecture with Unidirectional Data Flow (UDF)**:
  - Immutable UI state models (`BoardCell`, `TargetWord`, `LevelTemplate`, `PuzzleState`, `RackState`, `DropPreviewEvaluation`).
- **Dual-Axis Intersection & <1ms In-Memory Trie Engine (`WordTrieEngine` & `LexiFillEngine`)**:
  - Dropping a letter tile into an intersection blank simultaneously validates both the Horizontal (`ACROSS`) and Vertical (`DOWN`) words against the in-memory Trie.
- **100% Jetpack Compose Skeleton Grid & Spring-Physics Rack**:
  - Custom Compose `Canvas` (`LexiFillBoardCanvas.kt`) with magnetic slot hit-testing, ghost letter preview, and cascading word-completion flash animations.
  - Bouncy spring-animated draggable rack (`Animatable` with `Spring.DampingRatioMediumBouncy`) that snaps tiles back to the rack if released outside a valid blank.
- **Offline-First & Zero Network Quota Footprint**:
  - Curated and procedural Crossword Skeleton levels are stored in ultra-compact local skeleton strings and run 100% offline.
