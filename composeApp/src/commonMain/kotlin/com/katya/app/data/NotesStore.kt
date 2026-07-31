package com.katya.app.data

import com.katya.app.db.KatyaDatabase
import com.katya.app.db.Note
import kotlin.time.Clock

class NotesStore(private val db: KatyaDatabase?) {
    
    fun addNote(title: String, content: String, tags: String?) {
        db?.notesQueries?.insertNote(
            title = title,
            content = content,
            created_at = kotlin.time.Clock.System.now().toEpochMilliseconds(),
            updated_at = kotlin.time.Clock.System.now().toEpochMilliseconds(),
            tags = tags
        )
    }

    fun getAllNotes(): List<Note> {
        return db?.notesQueries?.selectAllNotes()?.executeAsList() ?: emptyList()
    }

    fun searchNotes(query: String): List<Note> {
        return db?.notesQueries?.searchNotes(query)?.executeAsList() ?: emptyList()
    }

    fun updateNote(id: Long, title: String, content: String, tags: String?) {
        db?.notesQueries?.updateNote(
            title = title,
            content = content,
            updated_at = kotlin.time.Clock.System.now().toEpochMilliseconds(),
            tags = tags,
            id = id
        )
    }

    fun deleteNote(id: Long) {
        db?.notesQueries?.deleteNote(id)
    }
}
