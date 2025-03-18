package ru.cbgr.adapter.xwiki.events;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.cbgr.adapter.xwiki.client.XWikiClient;
import ru.cbgr.adapter.xwiki.configuration.properties.ModificationCheckerProperties;
import ru.cbgr.adapter.xwiki.dto.xwiki.ModificationsResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.HistorySummary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "xwiki.modifications", name = "enabled", havingValue = "true")
public class ModificationCheckerEvent { // todo Пока на холде, требует доработок

    private final XWikiClient xWikiClient;
    private final ModificationCheckerProperties properties;

    /**
     * Хранит максимальное время модификации, обработанное в предыдущем запуске.
     * При первом запуске значение равно 0, поэтому все изменения будут считаться новыми.
     */
    private long lastCheckedModificationTimestamp = 0L;

    /**
     * Метод, запускаемый каждые 24 часа.
     * Параметры start и number позволяют получать историю изменений порциями.
     */
    @Scheduled(fixedRate = 30000) // 24 часа в миллисекундах = 86400000
    public void checkForNewModifications() {
        int start = 1;   // Начинаем с первого изменения
        int number = 10;  // Получаем 2 изменения за один запрос (пример)
        log.info("Запуск проверки изменений: start={}, number={}", start, number);

        ModificationsResponse response = xWikiClient.getModifications(start, number);
        if (response != null && response.getHistorySummaries() != null) {
            List<HistorySummary> summaries = response.getHistorySummaries();

            // Отбираем только те изменения, время которых больше, чем последнее обработанное
            List<HistorySummary> newModifications = summaries.stream()
                    .filter(summary -> summary.getModified() > lastCheckedModificationTimestamp)
                    .collect(Collectors.toList());

            if (!newModifications.isEmpty()) {
                log.info("Найдены новые изменения: {}", newModifications);
                // Обновляем lastCheckedModificationTimestamp до максимального времени среди новых изменений
                long maxTimestamp = newModifications.stream()
                        .mapToLong(HistorySummary::getModified)
                        .max()
                        .orElse(lastCheckedModificationTimestamp);
                lastCheckedModificationTimestamp = maxTimestamp;
            } else {
                log.info("Новых изменений не обнаружено.");
            }
        } else {
            log.warn("Получен пустой ответ от XWiki изменений.");
        }
    }
}
