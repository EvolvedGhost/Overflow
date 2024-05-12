package cn.evolvefield.onebot.client.connection

import cn.evole.onebot.sdk.util.JsonHelper.ignorable
import cn.evolvefield.onebot.client.handler.ActionHandler
import cn.evolvefield.onebot.client.handler.EventBus
import cn.evolvefield.onebot.client.util.ActionSendRequest
import cn.evolvefield.onebot.client.util.OnebotException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger

interface IAdapter {
    val scope: CoroutineScope
    val actionHandler: ActionHandler
    val logger: Logger

    fun onReceiveMessage(message: String) {
        try {
            val json = JsonParser.parseString(message).asJsonObject
            if (ignorable(json, META_EVENT, "") != HEART_BEAT) { // 过滤心跳
                logger.debug("Client received <-- {}", json.toString())

                if (json.has(API_RESULT_KEY)) { // 接口回调
                    actionHandler.onReceiveActionResp(json)
                } else scope.launch { // 处理事件
                    mutex.withLock {
                        EventBus.onReceive(message)
                    }
                }
            }
        } catch (e: JsonSyntaxException) {
            logger.error("Json语法错误: {}", message)
        } catch (e: OnebotException) {
            logger.error("解析异常: {}", e.info)
        }
    }

    fun unlockMutex() {
        runCatching {
            if (mutex.isLocked) mutex.unlock()
            if (ActionSendRequest.mutex.isLocked) ActionSendRequest.mutex.unlock()
        }
    }

    companion object {
        private const val META_EVENT = "meta_event_type"
        private const val API_RESULT_KEY = "echo"
        private const val HEART_BEAT = "heartbeat"

        val mutex = Mutex()
    }
}
