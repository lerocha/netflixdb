package com.github.lerocha.netflixdb.repository

import com.github.lerocha.netflixdb.entity.Show
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ShowRepository : JpaRepository<Show, UUID>
