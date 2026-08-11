package com.gcatcode.petmephone.core.domain.pet.sprite

/**
 * A declared sprite grid shape: [columns] left-to-right, [rows] top-to-bottom. Neither can be
 * inferred from pixel dimensions alone — a 1500x1500 image is equally consistent with 6x6, 5x5, and
 * 10x10 cells (`design.md` decision 13). This is the one shape both an import-time user declaration
 * and a bundled character's manifest describe.
 */
data class SpriteGridDeclaration(val columns: Int, val rows: Int)
