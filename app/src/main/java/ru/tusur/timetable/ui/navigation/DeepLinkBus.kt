package ru.tusur.timetable.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Очередь deep link'ов между Activity и ViewModel.
 *
 * Аргументы Navigation Compose не обновляются при `launchSingleTop` в уже
 * существующий back stack entry, поэтому ссылки из уведомлений/виджетов,
 * пришедшие в запущенное приложение, доставляются явно через эту шину.
 */
@Singleton
class DeepLinkBus @Inject constructor() {

    private val _pending = MutableStateFlow<AppLink?>(null)
    val pending: StateFlow<AppLink?> = _pending.asStateFlow()

    fun post(link: AppLink?) {
        if (link != null) _pending.value = link
    }

    /** Ссылка обработана — снимаем её, чтобы не применилась повторно. */
    fun consume(link: AppLink) {
        if (_pending.value == link) _pending.value = null
    }
}
