package com.lvyou.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvyou.entity.TravelHistory;
import com.lvyou.model.response.TravelPlanResponse;
import com.lvyou.service.HistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 JSON 文件的历史记录服务。
 *
 * MVP 阶段禁止引入数据库，因此历史数据只使用内存列表和本地 JSON 文件。
 */
@Slf4j
@Service
public class HistoryServiceImpl implements HistoryService {

    private final ObjectMapper objectMapper;
    private final List<TravelHistory> histories = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final Path historyFile;

    public HistoryServiceImpl(ObjectMapper objectMapper,
                              @Value("${history.file:data/history.json}") String historyFile) {
        this.objectMapper = objectMapper;
        this.historyFile = Path.of(historyFile);
    }

    @PostConstruct
    public synchronized void init() {
        loadFromFile();
    }

    @Override
    public synchronized TravelHistory saveHistory(TravelPlanResponse plan, String requestJson) {
        try {
            String resultJson = objectMapper.writeValueAsString(plan);

            String summary = "";
            int poiCount = 0;
            if (plan.getDays() != null && !plan.getDays().isEmpty()) {
                var firstDay = plan.getDays().get(0);
                summary = firstDay.getTheme() != null ? firstDay.getTheme() : "";
                poiCount = plan.getDays().stream()
                        .mapToInt(d -> d.getPois() != null ? d.getPois().size() : 0)
                        .sum();
            }

            TravelHistory history = TravelHistory.builder()
                    .id(idGenerator.getAndIncrement())
                    .fromCity(plan.getFrom())
                    .toCity(plan.getTo())
                    .days(plan.getTotalDays())
                    .preference(plan.getPreference())
                    .userIdea(plan.getUserIdea())
                    .resultJson(resultJson)
                    .summary(summary)
                    .poiCount(poiCount)
                    .createdAt(LocalDateTime.now())
                    .build();

            histories.add(history);
            persist();
            log.info("💾 历史记录已保存: id={}, {}→{} {}天", history.getId(),
                    plan.getFrom(), plan.getTo(), plan.getTotalDays());
            return history;
        } catch (Exception e) {
            log.error("保存历史记录失败", e);
            return null;
        }
    }

    @Override
    public synchronized List<TravelHistory> findAll() {
        return histories.stream()
                .sorted(Comparator.comparing(TravelHistory::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    @Override
    public synchronized TravelHistory findById(Long id) {
        return histories.stream()
                .filter(history -> history.getId() != null && history.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public synchronized void deleteById(Long id) {
        histories.removeIf(history -> history.getId() != null && history.getId().equals(id));
        persist();
        log.info("🗑️ 历史记录已删除: id={}", id);
    }

    private void loadFromFile() {
        try {
            if (!Files.exists(historyFile)) {
                return;
            }
            List<TravelHistory> loaded = objectMapper.readValue(
                    historyFile.toFile(),
                    new TypeReference<List<TravelHistory>>() {
                    });
            histories.clear();
            histories.addAll(loaded);
            long nextId = histories.stream()
                    .map(TravelHistory::getId)
                    .filter(id -> id != null)
                    .max(Long::compareTo)
                    .orElse(0L) + 1;
            idGenerator.set(nextId);
            log.info("已加载历史记录 {} 条", histories.size());
        } catch (Exception e) {
            log.warn("读取历史记录文件失败，将使用空历史: {}", e.getMessage());
        }
    }

    private void persist() {
        try {
            Path parent = historyFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(historyFile.toFile(), histories);
        } catch (Exception e) {
            log.warn("写入历史记录文件失败: {}", e.getMessage());
        }
    }
}
