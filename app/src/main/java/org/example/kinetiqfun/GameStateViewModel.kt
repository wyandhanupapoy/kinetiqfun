package org.example.kinetiqfun

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class GameStateViewModel : ViewModel() {

    private val _scoreP1 = MutableLiveData(0)
    val scoreP1: LiveData<Int> = _scoreP1

    private val _scoreP2 = MutableLiveData(0)
    val scoreP2: LiveData<Int> = _scoreP2

    private val _winner = MutableLiveData<String?>(null)
    val winner: LiveData<String?> = _winner

    private val _gameMode = MutableLiveData(OverlayView.GameMode.NONE)
    val gameMode: LiveData<OverlayView.GameMode> = _gameMode

    private val _rocks = MutableLiveData<List<OverlayView.Rock>>(emptyList())
    val rocks: LiveData<List<OverlayView.Rock>> = _rocks

    fun setGameMode(mode: OverlayView.GameMode) {
        _gameMode.value = mode
    }

    fun updateScores(p1: Int, p2: Int) {
        _scoreP1.value = p1
        _scoreP2.value = p2
    }

    fun setWinner(name: String?) {
        _winner.value = name
    }

    fun updateRocks(newRocks: List<OverlayView.Rock>) {
        _rocks.value = newRocks
    }

    fun resetGame() {
        _scoreP1.value = 0
        _scoreP2.value = 0
        _winner.value = null
        _rocks.value = emptyList()
    }
}
