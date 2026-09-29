package com.starrow.epgtimer.data.repository

import com.starrow.epgtimer.data.guide.parseAndKey
import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.model.RecSettingData
import com.starrow.epgtimer.data.model.SearchCondition
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class AutoAddRepositoryIntegrationTest {

    private lateinit var repository: EpgRepository
    private val addedIds = mutableListOf<Int>()

    @Before
    fun setUp() {
        val host = System.getenv("EDCB_TEST_HOST")
        assumeTrue("EDCB_TEST_HOST 未設定のため実サーバテストをスキップ", host != null)
        val port = System.getenv("EDCB_TEST_PORT")?.toIntOrNull() ?: 4510
        repository = EpgRepositoryImpl(store = InMemorySettingsStore())
        repository.updateServerConfig(ServerConfig(host = requireNotNull(host), port = port))
    }

    @After
    fun tearDown() {
        if (addedIds.isEmpty()) return
        runBlocking {
            repository.deleteAutoAdd(addedIds.toList()).getOrThrow()
        }
    }

    private fun sample(key: String) = EpgAutoAddData(
        dataId = EpgAutoAddData.NEW_DATA_ID,
        searchKey = SearchCondition.empty().copy(andKey = key),
        recSetting = RecSettingData.empty(),
        addCount = 0,
    )

    @Test
    fun `auto add list is readable`() = runBlocking {
        val list = repository.getAutoAdds().getOrThrow()
        list.forEach { item ->
            assertTrue("dataID が 0 のまま返っている", item.dataId != EpgAutoAddData.NEW_DATA_ID)
            assertTrue("addCount が負の値になっている", item.addCount >= 0)
        }
    }

    @Test
    fun `add then re-enumerate then delete round trip`() = runBlocking {
        val key = "ZZZ_TEST_KEY_12345ZZZ"
        val dataId = repository.addAutoAdd(sample(key)).getOrThrow()
        addedIds.add(dataId)

        val afterAdd = repository.getAutoAdds().getOrThrow()
        val added = afterAdd.firstOrNull { it.dataId == dataId }
        assertNotNull("再列挙で追加した条件が見つからない: $dataId", added)
        assertEquals(key, parseAndKey(added!!.searchKey.andKey).plain)

        repository.deleteAutoAdd(listOf(dataId)).getOrThrow()
        addedIds.remove(dataId)

        val afterDelete = repository.getAutoAdds().getOrThrow()
        assertTrue(
            "削除した条件がまだ残っている: $dataId",
            afterDelete.none { it.dataId == dataId },
        )
    }

    @Test
    fun `change is reflected after re-enumerating and can be reverted`() = runBlocking {
        val originalKey = "ZZZ_TEST_ORIG_12345ZZZ"
        val changedKey = "ZZZ_TEST_CHANGED_12345ZZZ"
        val dataId = repository.addAutoAdd(sample(originalKey)).getOrThrow()
        addedIds.add(dataId)

        repository.changeAutoAdd(
            sample(originalKey).copy(
                dataId = dataId,
                searchKey = SearchCondition.empty().copy(andKey = changedKey),
            ),
        ).getOrThrow()
        val changed = repository.getAutoAdds().getOrThrow().first { it.dataId == dataId }
        assertEquals(changedKey, parseAndKey(changed.searchKey.andKey).plain)

        repository.changeAutoAdd(sample(originalKey).copy(dataId = dataId)).getOrThrow()
        val reverted = repository.getAutoAdds().getOrThrow().first { it.dataId == dataId }
        assertEquals(originalKey, parseAndKey(reverted.searchKey.andKey).plain)
    }
}
