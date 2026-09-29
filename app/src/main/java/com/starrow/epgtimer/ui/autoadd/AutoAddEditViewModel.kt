package com.starrow.epgtimer.ui.autoadd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starrow.epgtimer.data.edcb.EdcbException
import com.starrow.epgtimer.data.edcb.ErrCode
import com.starrow.epgtimer.data.guide.buildAndKey
import com.starrow.epgtimer.data.guide.buildNotKey
import com.starrow.epgtimer.data.guide.parseAndKey
import com.starrow.epgtimer.data.guide.parseNotKey
import com.starrow.epgtimer.data.model.ContentData
import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.data.model.RecSettingData
import com.starrow.epgtimer.data.model.SearchCondition
import com.starrow.epgtimer.data.model.SearchDateInfo
import com.starrow.epgtimer.data.model.ServiceInfo
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.ui.errorText
import kotlinx.coroutines.launch

const val REC_NO_SERVICE_OFFSET = 40000
const val DEFAULT_REC_CHECK_DAYS = 6
val FREE_CA_LABELS = listOf("無料・有料を対象", "無料のみ", "有料のみ")

data class AutoAddForm(
    val dataId: Int = EpgAutoAddData.NEW_DATA_ID,
    val addCount: Int = 0,
    val andKey: String = "",
    val notKey: String = "",
    val note: String = "",
    val regExpFlag: Boolean = false,
    val aimaiFlag: Boolean = false,
    val titleOnlyFlag: Boolean = false,
    val caseSensitive: Boolean = false,
    val keyDisabled: Boolean = false,
    val durationMin: String = "0",
    val durationMax: String = "0",
    val genreLevel1: Set<Int> = emptySet(),
    val notContent: Boolean = false,
    val dayOfWeek: Set<Int> = emptySet(),
    val startHour: Int = 0,
    val startMin: Int = 0,
    val endHour: Int = 23,
    val endMin: Int = 59,
    val notDate: Boolean = false,
    val serviceKeys: Set<Long> = emptySet(),
    val freeCaFlag: Int = 0,
    val chkRecEnd: Boolean = false,
    val chkRecDay: String = DEFAULT_REC_CHECK_DAYS.toString(),
    val chkRecNoService: Boolean = false,
    val recSetting: RecSettingData = RecSettingData.empty(),
) {
    val isNew: Boolean
        get() = dataId == EpgAutoAddData.NEW_DATA_ID

    val effectiveAimai: Boolean
        get() = aimaiFlag && !regExpFlag
}

fun autoAddFormOf(item: EpgAutoAddData): AutoAddForm {
    val and = parseAndKey(item.searchKey.andKey)
    val not = parseNotKey(item.searchKey.notKey)
    val chkRecDay = if (item.searchKey.chkRecDay >= REC_NO_SERVICE_OFFSET) {
        item.searchKey.chkRecDay % 10000
    } else {
        item.searchKey.chkRecDay
    }
    val date = item.searchKey.dateList.firstOrNull()
    return AutoAddForm(
        dataId = item.dataId,
        addCount = item.addCount,
        andKey = and.plain,
        notKey = not.plain,
        note = not.note,
        regExpFlag = item.searchKey.regExpFlag != 0,
        aimaiFlag = item.searchKey.aimaiFlag != 0,
        titleOnlyFlag = item.searchKey.titleOnlyFlag != 0,
        caseSensitive = and.caseSensitive,
        keyDisabled = and.disabled,
        durationMin = and.durationMin.toString(),
        durationMax = and.durationMax.toString(),
        genreLevel1 = item.searchKey.contentList.map { it.nibbleLevel1 }.toSet(),
        notContent = item.searchKey.notContetFlag != 0,
        dayOfWeek = dateListOf(item.searchKey),
        startHour = date?.startHour ?: 0,
        startMin = date?.startMin ?: 0,
        endHour = date?.endHour ?: 23,
        endMin = date?.endMin ?: 59,
        notDate = item.searchKey.notDateFlag != 0,
        serviceKeys = item.searchKey.serviceList.toSet(),
        freeCaFlag = item.searchKey.freeCAFlag.coerceIn(0, 2),
        chkRecEnd = item.searchKey.chkRecEnd != 0,
        chkRecDay = chkRecDay.toString(),
        chkRecNoService = item.searchKey.chkRecDay >= REC_NO_SERVICE_OFFSET,
        recSetting = item.recSetting,
    )
}

private fun dateListOf(condition: SearchCondition): Set<Int> =
    condition.dateList.flatMap { list ->
        if (list.startDayOfWeek == list.endDayOfWeek) {
            listOf(list.startDayOfWeek)
        } else {
            (0..6).toList()
        }
    }.toSet()

fun autoAddFormOfSearchCondition(condition: SearchCondition): AutoAddForm {
    val and = parseAndKey(condition.andKey)
    val not = parseNotKey(condition.notKey)
    val date = condition.dateList.firstOrNull()
    val chkRecDay = if (condition.chkRecDay >= REC_NO_SERVICE_OFFSET) {
        condition.chkRecDay % 10000
    } else {
        condition.chkRecDay
    }
    return AutoAddForm(
        andKey = and.plain,
        notKey = not.plain,
        note = not.note,
        regExpFlag = condition.regExpFlag != 0,
        aimaiFlag = condition.aimaiFlag != 0,
        titleOnlyFlag = condition.titleOnlyFlag != 0,
        caseSensitive = and.caseSensitive,
        keyDisabled = and.disabled,
        durationMin = and.durationMin.toString(),
        durationMax = and.durationMax.toString(),
        genreLevel1 = condition.contentList.map { it.nibbleLevel1 }.toSet(),
        notContent = condition.notContetFlag != 0,
        dayOfWeek = dateListOf(condition),
        startHour = date?.startHour ?: 0,
        startMin = date?.startMin ?: 0,
        endHour = date?.endHour ?: 23,
        endMin = date?.endMin ?: 59,
        notDate = condition.notDateFlag != 0,
        serviceKeys = condition.serviceList.toSet(),
        freeCaFlag = condition.freeCAFlag.coerceIn(0, 2),
        chkRecEnd = condition.chkRecEnd != 0,
        chkRecDay = chkRecDay.toString(),
        chkRecNoService = condition.chkRecDay >= REC_NO_SERVICE_OFFSET,
    )
}

fun AutoAddForm.toAutoAddData(): EpgAutoAddData {
    val days = chkRecDay.toIntOrNull()?.coerceIn(0, 9999) ?: DEFAULT_REC_CHECK_DAYS
    val searchKey = SearchCondition.empty().copy(
        andKey = buildAndKey(
            plain = andKey,
            disabled = keyDisabled,
            caseSensitive = caseSensitive,
            durationMin = durationMin.toIntOrNull() ?: 0,
            durationMax = durationMax.toIntOrNull() ?: 0,
        ),
        notKey = buildNotKey(note, notKey),
        regExpFlag = if (regExpFlag) 1 else 0,
        titleOnlyFlag = if (titleOnlyFlag) 1 else 0,
        contentList = genreLevel1.sorted().map { ContentData(it, 0xFF, 0, 0) },
        dateList = if (dayOfWeek.isEmpty()) {
            emptyList()
        } else {
            dayOfWeek.sorted().map { day ->
                SearchDateInfo(day, startHour, startMin, day, endHour, endMin)
            }
        },
        serviceList = serviceKeys.sorted(),
        aimaiFlag = if (effectiveAimai) 1 else 0,
        notContetFlag = if (notContent) 1 else 0,
        notDateFlag = if (notDate) 1 else 0,
        freeCAFlag = freeCaFlag,
        chkRecEnd = if (chkRecEnd) 1 else 0,
        chkRecDay = if (chkRecNoService) days % 10000 + REC_NO_SERVICE_OFFSET else days,
    )
    return EpgAutoAddData(
        dataId = dataId,
        searchKey = searchKey,
        recSetting = recSetting,
        addCount = addCount,
    )
}

class AutoAddEditViewModel(
    private val repository: EpgRepository,
    initial: AutoAddForm,
) : ViewModel() {

    var form by mutableStateOf(initial)
        private set

    var services by mutableStateOf<List<ServiceInfo>>(emptyList())
        private set

    var submitting by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            repository.getServices().onSuccess { services = it }
        }
    }

    fun update(block: (AutoAddForm) -> AutoAddForm) {
        form = block(form)
    }

    fun save(onResult: (Result<Int>) -> Unit) {
        if (submitting) return
        submitting = true
        error = null
        viewModelScope.launch {
            val item = form.toAutoAddData()
            val result = if (item.dataId == EpgAutoAddData.NEW_DATA_ID) {
                repository.addAutoAdd(item)
            } else {
                repository.changeAutoAdd(item).map { item.dataId }
            }
            submitting = false
            result.onFailure {
                error = if (it is EdcbException && it.code == ErrCode.CMD_NON_SUPPORT) {
                    "サーバが自動EPG予約登録に対応していません"
                } else {
                    errorText(it)
                }
            }
            onResult(result)
        }
    }
}
