package com.mjzeolla.mindsEye.models

import android.util.Log
import com.mjzeolla.mindsEye.utils.*
import com.mjzeolla.mindsEye.utils.ANIMAL_ICONS

class MemoryGame(private val boardSize: BoardSize, private val customImages: List<String>?, private val category: String){

    companion object{
        private  const val TAG = "MemoryGame"
    }

    val cards: List<MemoryCard>
    var numPairsFound = 0

    var numFlips = 0
    private var indexOfSelectingSingleCard: Int? = null

    init {
        if(customImages == null){
            if(category == "Sports"){
                Log.i(TAG, "Category is Sports")
                val chosenImages = SPORTS_ICONS.shuffled().take(boardSize.getNumPairs())
                val randomizedImages = (chosenImages + chosenImages).shuffled()
                cards = randomizedImages.map { MemoryCard(it) }
            }

            else if(category == "Video Games"){
                Log.i(TAG, "Category is Video Games")
                val chosenImages = VIDEO_GAME_ICONS.shuffled().take(boardSize.getNumPairs())
                val randomizedImages = (chosenImages + chosenImages).shuffled()
                cards = randomizedImages.map { MemoryCard(it) }
            }

            else if(category == "Animals"){
                Log.i(TAG, "Category is Animals")
                val chosenImages = ANIMAL_ICONS.shuffled().take(boardSize.getNumPairs())
                val randomizedImages = (chosenImages + chosenImages).shuffled()
                cards = randomizedImages.map { MemoryCard(it) }
            }

            else {
                Log.i(TAG, "Category is tail")
                val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
                val randomizedImages = (chosenImages + chosenImages).shuffled()
                cards = randomizedImages.map { MemoryCard(it) }
            }
        }
        else {
            val randomizedImages = (customImages + customImages).shuffled()
            cards = randomizedImages.map{ MemoryCard(it.hashCode(), it) }
        }
        //This gets a random number of item pairs from the list
        //Adds the duplicates to the list and shuffles

    }

    fun flipCard(position: Int) : Boolean {
        numFlips++
        val card = cards[position]
        //Three cases:
            //1. 0 cards flipped => restore cards + flip selected cards
            //2. 1 card flipped => flip over selected card + check if match
            //3. 2 cards flipped => restore cards + flip selected
        var foundMatch = false
        if(indexOfSelectingSingleCard == null){
            //0 or 2 cards are flipped
            restoreCards()
            indexOfSelectingSingleCard = position
        }
        else{
            //1 card is flipped
            foundMatch = checkForMatch(indexOfSelectingSingleCard!!, position)
            indexOfSelectingSingleCard = null
        }

        card.isFaceUp = !card.isFaceUp
        return foundMatch

    }

    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        if(cards[position1].identifier != cards[position2].identifier){
            return false
        }
        cards[position1].isMatch = true
        cards[position2].isMatch = true
        numPairsFound++
        return true
    }


    private fun restoreCards() {
        for(card in cards){
            if(!card.isMatch){
                card.isFaceUp = false
            }
        }
    }

    fun gameOver(): Boolean {
        return numPairsFound == boardSize.getNumPairs()
    }

    fun isCardFaceUp(position: Int): Boolean {
        return cards[position].isFaceUp
    }

    fun getNumMoves(): Int {
        return numFlips/2
    }
}