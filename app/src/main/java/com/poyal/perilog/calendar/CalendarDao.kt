package com.poyal.perilog.calendar

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_connections") suspend fun connections(): List<CalendarConnection>
    @Query("SELECT * FROM calendar_connections") fun observeConnections(): Flow<List<CalendarConnection>>
    @Query("SELECT * FROM calendar_connections WHERE id=:id") suspend fun connection(id: String): CalendarConnection?
    @Upsert suspend fun put(value: CalendarConnection)
    @Query("SELECT * FROM calendar_links WHERE connectionId=:id") suspend fun links(id: String): List<CalendarLink>
    @Query("SELECT * FROM calendar_links") fun observeLinks(): Flow<List<CalendarLink>>
    @Query("SELECT * FROM calendar_links WHERE id=:id") suspend fun link(id: String): CalendarLink?
    @Upsert suspend fun put(value: CalendarLink)
    @Query("SELECT * FROM calendar_jobs") suspend fun jobs(): List<CalendarJob>
    @Query("SELECT * FROM calendar_jobs") fun observeJobs(): Flow<List<CalendarJob>>
    @Query("SELECT * FROM calendar_jobs WHERE linkId=:id") suspend fun job(id: String): CalendarJob?
    @Upsert suspend fun put(value: CalendarJob)
    @Query("DELETE FROM calendar_jobs WHERE linkId=:id AND revision=:revision") suspend fun complete(id: String, revision: Long)
    @Query("DELETE FROM calendar_jobs WHERE connectionId=:id") suspend fun clearJobs(id: String)
    @Query("SELECT * FROM calendar_device_state WHERE id=1") suspend fun deviceState(): CalendarDeviceState?
    @Upsert suspend fun put(value: CalendarDeviceState)
}
