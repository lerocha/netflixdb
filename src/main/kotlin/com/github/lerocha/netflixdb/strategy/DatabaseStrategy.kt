package com.github.lerocha.netflixdb.strategy

import com.github.lerocha.netflixdb.entity.Movie

interface DatabaseStrategy {
    fun getInsertStatement(movie: Movie): String

    fun getInsertStatement(movies: List<Movie>): String
}
