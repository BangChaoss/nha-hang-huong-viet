package com.huongViet.table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FloorPlanService {

    private static final Logger logger = LoggerFactory.getLogger(FloorPlanService.class);

    private final FloorPlanLayoutRepository layoutRepository;
    private final TableRepository tableRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public FloorPlanService(FloorPlanLayoutRepository layoutRepository,
                            TableRepository tableRepository,
                            SimpMessagingTemplate messagingTemplate) {
        this.layoutRepository = layoutRepository;
        this.tableRepository = tableRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public List<FloorPlanLayoutResponse> getAll() {
        List<FloorPlanLayout> layouts = layoutRepository.findAll();
        List<FloorPlanLayoutResponse> responses = new ArrayList<>();

        for (FloorPlanLayout layout : layouts) {
            tableRepository.findByIdActiveWithZone(layout.getTableId())
                .ifPresent(table -> responses.add(FloorPlanLayoutResponse.from(layout, table)));
        }

        return responses;
    }

    @Transactional
    public List<FloorPlanLayoutResponse> saveAll(List<FloorPlanLayoutRequest> requests) {
        List<FloorPlanLayoutResponse> responses = new ArrayList<>();

        for (FloorPlanLayoutRequest request : requests) {
            RestaurantTable table = tableRepository.findByIdActiveWithZone(request.tableId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bàn với ID " + request.tableId() + " không tồn tại hoặc đã bị xóa."
                ));

            FloorPlanLayout layout = layoutRepository.findByTableId(request.tableId())
                .orElseGet(() -> new FloorPlanLayout(
                    UUID.randomUUID().toString(),
                    request.tableId(),
                    request.positionX(),
                    request.positionY(),
                    request.width(),
                    request.height(),
                    request.rotation(),
                    request.shape()
                ));

            layout.setPositionX(request.positionX());
            layout.setPositionY(request.positionY());
            layout.setWidth(request.width());
            layout.setHeight(request.height());
            layout.setRotation(request.rotation());
            layout.setShape(request.shape());

            FloorPlanLayout saved = layoutRepository.save(layout);
            responses.add(FloorPlanLayoutResponse.from(saved, table));
        }

        logger.info("Saved batch floor plan layout positions: count={}", responses.size());
        broadcastFloorPlanChange(responses);
        return responses;
    }

    private void broadcastFloorPlanChange(List<FloorPlanLayoutResponse> layouts) {
        try {
            messagingTemplate.convertAndSend("/topic/floor-plan", layouts);
            logger.debug("Broadcasted floor plan layout update");
        } catch (Exception ex) {
            logger.error("Failed to broadcast floor plan update via WebSocket: ", ex);
        }
    }
}
