package com.aistudio.zoya.domain.model

enum class Sentiment {
    Neutral, Happy, Sad, Angry, Excited, Thinking
}

sealed class AssistantState {
    object Idle : AssistantState()
    object Listening : AssistantState()
    data class Thinking(val sentiment: Sentiment = Sentiment.Thinking) : AssistantState()
    data class Speaking(val sentiment: Sentiment = Sentiment.Neutral) : AssistantState()
    object Sleeping : AssistantState()
    data class Error(val message: String) : AssistantState()
    object Offline : AssistantState()
}
