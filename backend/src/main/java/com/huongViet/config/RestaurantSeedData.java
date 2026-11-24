package com.huongViet.config;

import com.huongViet.table.FloorPlanLayout;
import com.huongViet.table.FloorPlanLayoutRepository;
import com.huongViet.table.FloorPlanShape;
import com.huongViet.table.RestaurantTable;
import com.huongViet.table.TableRepository;
import com.huongViet.table.TableZone;
import com.huongViet.table.ZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seeds the physical restaurant layout (zones, tables and their floor plan
 * positions) on first startup so the reservation and POS modules have data to
 * work with. Only runs when the zones table is still empty.
 */
@Component
public class RestaurantSeedData implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(RestaurantSeedData.class);

    private static final String ZONE_INDOOR = "Trong Nhà";
    private static final String ZONE_GARDEN = "Sân Vườn";
    private static final String ZONE_VIP = "Phòng VIP";

    private final ZoneRepository zoneRepository;
    private final TableRepository tableRepository;
    private final FloorPlanLayoutRepository floorPlanLayoutRepository;

    public RestaurantSeedData(ZoneRepository zoneRepository,
                              TableRepository tableRepository,
                              FloorPlanLayoutRepository floorPlanLayoutRepository) {
        this.zoneRepository = zoneRepository;
        this.tableRepository = tableRepository;
        this.floorPlanLayoutRepository = floorPlanLayoutRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (zoneRepository.countByDeletedAtIsNull() > 0) {
            logger.info("Restaurant layout already seeded. Skipping.");
            return;
        }

        TableZone indoor = createZone(ZONE_INDOOR, false);
        TableZone garden = createZone(ZONE_GARDEN, false);
        TableZone vip = createZone(ZONE_VIP, true);

        // Trong Nhà (30 chỗ) - X in [60..540], Y in [70..290]
        createTableWithLayout(indoor, "Bàn 01", 4, 60.0, 70.0, 90.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(indoor, "Bàn 02", 4, 180.0, 70.0, 90.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(indoor, "Bàn 03A", 2, 300.0, 70.0, 60.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(indoor, "Bàn 03B", 4, 370.0, 70.0, 80.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(indoor, "Bàn 04", 4, 60.0, 190.0, 90.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(indoor, "Bàn 05A", 4, 180.0, 190.0, 80.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(indoor, "Bàn 05B", 4, 270.0, 190.0, 80.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(indoor, "Bàn 13", 2, 480.0, 70.0, 60.0, 60.0, FloorPlanShape.CIRCLE);
        createTableWithLayout(indoor, "Bàn 14", 2, 480.0, 150.0, 60.0, 60.0, FloorPlanShape.CIRCLE);
        createTableWithLayout(indoor, "Bàn 15", 2, 480.0, 230.0, 60.0, 60.0, FloorPlanShape.CIRCLE);

        // Sân Vườn (22 chỗ) - X in [580..960], Y in [70..290]
        createTableWithLayout(garden, "Bàn 06", 4, 580.0, 70.0, 90.0, 90.0, FloorPlanShape.CIRCLE);
        createTableWithLayout(garden, "Bàn 07A", 2, 700.0, 70.0, 60.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(garden, "Bàn 07B", 4, 770.0, 70.0, 80.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(garden, "Bàn 08", 4, 580.0, 190.0, 90.0, 90.0, FloorPlanShape.CIRCLE);
        createTableWithLayout(garden, "Bàn 09A", 4, 700.0, 190.0, 80.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(garden, "Bàn 09B", 4, 790.0, 190.0, 80.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(garden, "Bàn 16", 2, 900.0, 70.0, 60.0, 60.0, FloorPlanShape.CIRCLE);
        createTableWithLayout(garden, "Bàn 17", 2, 900.0, 160.0, 60.0, 60.0, FloorPlanShape.CIRCLE);

        // Phòng VIP (32 chỗ) - X in [60..520], Y in [385..505]
        createTableWithLayout(vip, "Bàn 10", 10, 60.0, 400.0, 140.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(vip, "Bàn 11", 12, 230.0, 400.0, 140.0, 90.0, FloorPlanShape.RECTANGLE);
        createTableWithLayout(vip, "Bàn 12", 10, 400.0, 385.0, 120.0, 120.0, FloorPlanShape.CIRCLE);

        logger.info("Seeded {} zones and {} tables.",
            zoneRepository.countByDeletedAtIsNull(), tableRepository.countByDeletedAtIsNull());
    }

    private TableZone createZone(String name, boolean isVip) {
        return zoneRepository.save(new TableZone(UUID.randomUUID().toString(), name, isVip));
    }

    private void createTableWithLayout(TableZone zone, String tableNumber, int capacity,
                                       double x, double y, double w, double h, FloorPlanShape shape) {
        RestaurantTable table = tableRepository.save(new RestaurantTable(
            UUID.randomUUID().toString(),
            zone.getId(),
            tableNumber,
            capacity
        ));

        floorPlanLayoutRepository.save(new FloorPlanLayout(
            UUID.randomUUID().toString(),
            table.getId(),
            x,
            y,
            w,
            h,
            0.0,
            shape
        ));
    }
}
