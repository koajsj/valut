package com.offlinevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.data.backup.BackupManager
import com.offlinevault.data.backup.ImportResult
import com.offlinevault.data.model.PasswordEntity
import com.offlinevault.data.repository.PasswordRepository
import com.offlinevault.data.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PasswordListViewModel(
    private val mimaCangku: PasswordRepository,
    private val mimakuCangku: VaultRepository,
    private val beifenGuanli: BackupManager
) : ViewModel() {

    private val mimakuId = MutableStateFlow("")
    private val chaxun = MutableStateFlow("")
    private val biaoqianGuolv = MutableStateFlow<String?>(null)

    val sousuoChaxun: StateFlow<String> = chaxun.asStateFlow()
    val dangqianBiaoqian: StateFlow<String?> = biaoqianGuolv.asStateFlow()

    private val _mimakuMingcheng = MutableStateFlow("")
    val mimakuMingcheng: StateFlow<String> = _mimakuMingcheng.asStateFlow()

    private val _cuowuXinxi = MutableStateFlow<String?>(null)
    val cuowuXinxi: StateFlow<String?> = _cuowuXinxi.asStateFlow()

    // Debounce only the typed query (blank emits immediately so the initial load isn't delayed).
    private val chaxunFangdou = chaxun.debounce { if (it.isBlank()) 0L else 200L }

    val tiaomu: StateFlow<List<PasswordEntity>> =
        combine(mimakuId, chaxunFangdou, biaoqianGuolv) { kuId, cxWen, biaoqian -> Triple(kuId, cxWen, biaoqian) }
            .flatMapLatest { (kuId, cxWen, biaoqian) ->
                when {
                    kuId.isEmpty() -> kotlinx.coroutines.flow.flowOf(emptyList())
                    biaoqian != null -> mimaCangku.byTag(kuId, biaoqian)
                    cxWen.isNotBlank() -> mimaCangku.search(kuId, cxWen.trim())
                    else -> mimaCangku.passwordsByVault(kuId)
                }
            }
            .catch { yichang ->
                if (yichang is CancellationException) throw yichang
                _cuowuXinxi.value = "无法读取密码数据"
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All distinct tags present in the current vault, for the filter row. */
    val biaoqianLiebiao: StateFlow<List<String>> =
        mimakuId.flatMapLatest { kuId ->
            if (kuId.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
            else mimaCangku.passwordsByVault(kuId)
        }.map { liebiao ->
            liebiao.flatMap { it.tags.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }.catch { yichang ->
            if (yichang is CancellationException) throw yichang
            emit(emptyList())
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Opens the single default vault, creating it on first run. Used as the app's home. */
    fun jiazaiMoren() {
        viewModelScope.launch {
            try {
                val mimaku = mimakuCangku.ensureDefault()
                mimakuId.value = mimaku.id
                _mimakuMingcheng.value = mimaku.name
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _cuowuXinxi.value = "无法打开密码库，请重新锁定后再试"
            }
        }
    }

    /** Current vault id, or empty if not loaded yet. */
    fun dangqianMimakuId(): String = mimakuId.value

    fun daoruJson(neirong: String, mima: String, huidiao: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val jieguo = try {
                withContext(Dispatchers.Default) { beifenGuanli.importJsonBackup(neirong, mima) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ImportResult(0, 0, 0, listOf("导入失败，请检查文件后重试"))
            }
            huidiao(jieguo)
        }
    }

    fun daoruCsv(neirong: String, huidiao: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val jieguo = try {
                val mubiao = mimakuId.value.ifEmpty { mimakuCangku.ensureDefault().id }
                withContext(Dispatchers.Default) { beifenGuanli.importChromeCsv(neirong, mubiao) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ImportResult(0, 0, 0, listOf("导入失败，请检查文件后重试"))
            }
            huidiao(jieguo)
        }
    }

    fun qingchuCuowu() {
        _cuowuXinxi.value = null
    }

    fun shezhiChaxun(zhi: String) { chaxun.value = zhi }

    fun qiehuanBiaoqian(biaoqian: String) {
        biaoqianGuolv.value = if (biaoqianGuolv.value == biaoqian) null else biaoqian
    }
}
