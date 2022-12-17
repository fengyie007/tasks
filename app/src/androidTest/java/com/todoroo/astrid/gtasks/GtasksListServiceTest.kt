package com.todoroo.astrid.gtasks

import com.google.api.services.tasks.model.TaskList
import com.natpryce.makeiteasy.MakeItEasy.with
import com.todoroo.astrid.service.TaskDeleter
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.tasks.LocalBroadcastManager
import org.tasks.data.CaldavAccount
import org.tasks.data.CaldavDao
import org.tasks.data.GoogleTaskListDao
import org.tasks.injection.InjectingTestCase
import org.tasks.injection.ProductionModule
import org.tasks.makers.GtaskListMaker.ID
import org.tasks.makers.GtaskListMaker.NAME
import org.tasks.makers.GtaskListMaker.REMOTE_ID
import org.tasks.makers.GtaskListMaker.newGtaskList
import org.tasks.makers.RemoteGtaskListMaker
import org.tasks.makers.RemoteGtaskListMaker.newRemoteList
import javax.inject.Inject

@UninstallModules(ProductionModule::class)
@HiltAndroidTest
class GtasksListServiceTest : InjectingTestCase() {
    @Inject lateinit var taskDeleter: TaskDeleter
    @Inject lateinit var localBroadcastManager: LocalBroadcastManager
    @Inject lateinit var googleTaskListDao: GoogleTaskListDao
    @Inject lateinit var caldavDao: CaldavDao

    private lateinit var gtasksListService: GtasksListService

    @Before
    override fun setUp() {
        super.setUp()
        gtasksListService = GtasksListService(googleTaskListDao, taskDeleter, localBroadcastManager)
    }

    @Test
    fun testCreateNewList() = runBlocking {
        setLists(
                newRemoteList(
                        with(RemoteGtaskListMaker.REMOTE_ID, "1"), with(RemoteGtaskListMaker.NAME, "Default")))
        assertEquals(
                newGtaskList(with(ID, 1L), with(REMOTE_ID, "1"), with(NAME, "Default")),
                googleTaskListDao.getById(1L))
    }

    @Test
    fun testGetListByRemoteId() = runBlocking {
        val list = newGtaskList(with(REMOTE_ID, "1"))
        list.id = googleTaskListDao.insertOrReplace(list)
        assertEquals(list, googleTaskListDao.getByRemoteId("1"))
    }

    @Test
    fun testGetListReturnsNullWhenNotFound() = runBlocking {
        assertNull(googleTaskListDao.getByRemoteId("1"))
    }

    @Test
    fun testDeleteMissingList() = runBlocking {
        googleTaskListDao.insertOrReplace(newGtaskList(with(ID, 1L), with(REMOTE_ID, "1")))
        val taskList = newRemoteList(with(RemoteGtaskListMaker.REMOTE_ID, "2"))
        setLists(taskList)
        assertEquals(
                listOf(newGtaskList(with(ID, 2L), with(REMOTE_ID, "2"))),
                googleTaskListDao.getLists("account"))
    }

    @Test
    fun testUpdateListName() = runBlocking {
        googleTaskListDao.insertOrReplace(
                newGtaskList(with(ID, 1L), with(REMOTE_ID, "1"), with(NAME, "oldName")))
        setLists(
                newRemoteList(
                        with(RemoteGtaskListMaker.REMOTE_ID, "1"), with(RemoteGtaskListMaker.NAME, "newName")))
        assertEquals("newName", googleTaskListDao.getById(1)!!.title)
    }

    @Test
    fun testNewListLastSyncIsZero() = runBlocking {
        setLists(TaskList().setId("1"))
        assertEquals(0L, googleTaskListDao.getByRemoteId("1")!!.lastSync)
    }

    private suspend fun setLists(vararg list: TaskList) {
        val account = CaldavAccount().apply {
            username = "account"
            uuid = "account"
        }
        caldavDao.insert(account)
        gtasksListService.updateLists(account, listOf(*list))
    }
}