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

        int index = 0;
        index = createTables(indoor, index, new int[]{4, 4, 6, 4, 8});
        index = createTables(garden, index, new int[]{4, 6, 4, 8});
        createTables(vip, index, new int[]{10, 12, 10});

        logger.info("Seeded {} zones and {} tables.",
            zoneRepository.countByDeletedAtIsNull(), tableRepository.countByDeletedAtIsNull());
    }

    private TableZone createZone(String name, boolean isVip) {
        return zoneRepository.save(new TableZone(UUID.randomUUID().toString(), name, isVip));
    }

    /**
     * Creates a run of tables inside a zone and lays them out on a grid.
     *
     * @return the next free table index
     */
    private int createTables(TableZone zone, int startIndex, int[] capacities) {
        int index = startIndex;
        for (int capacity : capacities) {
            index++;
            RestaurantTable table = tableRepository.save(new RestaurantTable(
                UUID.randomUUID().toString(),
                zone.getId(),
                String.format("Bàn %02d", index),
                capacity
            ));

            // Bố trí lưới 3 cột x 4 hàng trên sơ đồ sàn.
            int column = (index - 1) % 3;
            int row = (index - 1) / 3;
            floorPlanLayoutRepository.save(new FloorPlanLayout(
                UUID.randomUUID().toString(),
                table.getId(),
                60.0 + column * 140.0,
                60.0 + row * 120.0,
                100.0,
                90.0,
                0.0,
                FloorPlanShape.RECTANGLE
            ));
        }
        return index;
    }
}
