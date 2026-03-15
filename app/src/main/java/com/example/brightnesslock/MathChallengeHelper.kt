package com.example.brightnesslock

/**
 * Generates simple math challenges (addition/subtraction with numbers 1–10)
 * used to gate access to the parent Set Mode.
 */
object MathChallengeHelper {

    data class Challenge(val question: String, val answer: Int)

    /**
     * Returns a new random challenge each time it is called.
     * Operations: addition (+) or subtraction (–).
     * Operands: random integers in [1, 10].
     * Subtraction is ordered so the result is always non-negative.
     */
    fun generate(): Challenge {
        val a = (1..10).random()
        val b = (1..10).random()
        return if ((0..1).random() == 0) {
            Challenge("$a + $b = ?", a + b)
        } else {
            // Ensure result >= 0 by putting the larger number first
            val big = maxOf(a, b)
            val small = minOf(a, b)
            Challenge("$big - $small = ?", big - small)
        }
    }

    /** Returns true when the user-supplied answer matches the correct answer. */
    fun verify(userAnswer: Int, challenge: Challenge): Boolean =
        userAnswer == challenge.answer
}
