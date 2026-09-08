package com.example.tennisscorer.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BounceRepositoryTest {

    private val mockDao = mockk<BounceDao>(relaxed = true)
    private val repo = BounceRepository(mockDao)

    @Test fun `insert delegates to dao`() = runBlocking {
        val record = BounceRecord(matchId = 1L, x = 5f, y = 10f, player = 1)
        repo.insert(record)
        coVerify { mockDao.insert(record) }
    }

    @Test fun `getByMatchId returns dao result`() = runBlocking {
        val records = listOf(BounceRecord(matchId = 42L, x = 3f, y = 7f, player = 2))
        coEvery { mockDao.getByMatchId(42L) } returns records
        val result = repo.getByMatchId(42L)
        assertEquals(records, result)
    }

    @Test fun `getByMatchId for unknown matchId returns empty list`() = runBlocking {
        coEvery { mockDao.getByMatchId(1L) } returns emptyList()
        val result = repo.getByMatchId(1L)
        assertEquals(emptyList<BounceRecord>(), result)
    }
}
