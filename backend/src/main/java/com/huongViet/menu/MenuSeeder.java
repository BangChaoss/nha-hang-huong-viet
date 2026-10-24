package com.huongViet.menu;

import com.huongViet.upload.ImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Seeds the restaurant menu (categories + dishes) on first startup so the
 * public menu page and the admin screens have data to work with.
 * Only runs when the categories table is still empty.
 *
 * <p>Ảnh món ăn được sinh tự động dưới dạng SVG và lưu trong thư mục ảnh cục bộ,
 * nên database mới dùng được mà không cần mạng. Quản lý có thể thay bằng ảnh thật
 * từ màn Quản lý thực đơn.</p>
 */
@Component
public class MenuSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MenuSeeder.class);

    private static final String MENU_DIRECTORY = "menu";

    /** Màu nền ảnh minh hoạ theo từng danh mục. */
    private static final String COLOR_APPETIZER = "#7c6f3f";
    private static final String COLOR_MAIN = "#3f5c4a";
    private static final String COLOR_HOTPOT = "#8a4b3c";
    private static final String COLOR_DESSERT = "#5c4a6b";

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final ImageStorageService imageStorageService;

    public MenuSeeder(CategoryRepository categoryRepository,
                      MenuItemRepository menuItemRepository,
                      ImageStorageService imageStorageService) {
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.imageStorageService = imageStorageService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (categoryRepository.countByDeletedAtIsNull() > 0) {
            logger.info("Menu already seeded. Skipping.");
            return;
        }

        Category appetizer = createCategory("Khai vị", 1);
        Category mainCourse = createCategory("Món chính", 2);
        Category hotpot = createCategory("Lẩu & Nướng", 3);
        Category dessert = createCategory("Tráng miệng & Đồ uống", 4);

        // ---- Khai vị ----
        createItem(appetizer, "Gỏi Cuốn Tôm Thịt Ba Chỉ",
            "Bánh tráng cuối tôm, ba chỉ, bún và rau thơm, chấm tương đậu.",
            "60000", COLOR_APPETIZER, true);
        createItem(appetizer, "Chả Giò Rế Cua Biển",
            "Chả giò vỏ rế giòn rụm, nhân cua biển và nấm mèo.",
            "70000", COLOR_APPETIZER, true);
        createItem(appetizer, "Nem Chua Rán Muối",
            "Nem lụa cuộn bìn thơm, rán giòn, ăn kèm chấm mắm tôm.",
            "85000", COLOR_APPETIZER, true);
        createItem(appetizer, "Gỏi Vịt Bát Lôi",
            "Vịt áp chảo mềm, xà lách giòn, điều chấm mè tôm và đậu tương.",
            "88000", COLOR_APPETIZER, true);
        createItem(appetizer, "Bánh Xèo Nhân Tôm Thịt",
            "Bánh xèo giòn rụm nhân tôm thịt, ăn kèm rau sống và nước chấm.",
            "75000", COLOR_APPETIZER, true);

        // ---- Món chính ----
        createItem(mainCourse, "Phở Bò Tái Lăn Hà Nội",
            "Nước dùng xương ninh 12 giờ, thịt bò tái lăn gừng tỏi.",
            "85000", COLOR_MAIN, true);
        createItem(mainCourse, "Bún Chả Hương Quê",
            "Chả nướng than hoa, nước mắm chua ngọt, rau sống.",
            "75000", COLOR_MAIN, true);
        createItem(mainCourse, "Cá Kho Tộ Niêu Đất",
            "Cá basa kho tiêu nước màu dừa trong niêu đất nung.",
            "120000", COLOR_MAIN, false);
        createItem(mainCourse, "Cơm Tấm Bì Chả Lòt",
            "Cơm tấm dẻo, bì heo giòn, chả lòt nước mắm và rau xanh.",
            "82000", COLOR_MAIN, true);
        createItem(mainCourse, "Gà Kho Tộ Sơn Tây",
            "Gà ta kho tộ nước dừa, thơm dứa và nước mắm rượu.",
            "95000", COLOR_MAIN, true);
        createItem(mainCourse, "Mì Quảng Tôm Thịt",
            "Mì Quảng sợi to, nước dùng nhợt, tôm thịt và rau thơm.",
            "72000", COLOR_MAIN, true);

        // ---- Lẩu & Nướng ----
        createItem(hotpot, "Lẩu Riêu Cua Bắp Bò",
            "Riêu cua đồng, bắp bò, sườn sụn, đậu phụ chiên và rau đồng.",
            "380000", COLOR_HOTPOT, true);
        createItem(hotpot, "Bò Nướng Lá Lốt",
            "Bò cuộn lá lốt nướng than, mỡ hành, đậu phộng rang.",
            "140000", COLOR_HOTPOT, true);
        createItem(hotpot, "Lẩu Thái Chua Cay",
            "Lẩu thái chua cay, tôm, mực, thịt bò và rau nhúng.",
            "320000", COLOR_HOTPOT, true);
        createItem(hotpot, "Mực Nướng Mắm Nhứt",
            "Mực lá nướng mắm nhứt, dọn nướng trên đĩa lá chuối.",
            "180000", COLOR_HOTPOT, false);
        createItem(hotpot, "Lẩu Gà Lá Ép",
            "Lẩu gà lá ép thanh mát, nấm, đậu hũ và bắp non.",
            "280000", COLOR_HOTPOT, true);

        // ---- Tráng miệng & Đồ uống ----
        createItem(dessert, "Chè Hạt Sen Long Nhãn",
            "Hạt sen Huế bùi, long nhãn Hưng Yên, nước đường phèn.",
            "35000", COLOR_DESSERT, true);
        createItem(dessert, "Trà Sen Vàng Cố Đô",
            "Trà Thái Nguyên ướp sen Tây Hồ, phục vụ ấm.",
            "40000", COLOR_DESSERT, true);
        createItem(dessert, "Bánh Chuối Nướng Mật Ong",
            "Bánh chuối nướng mật ong, dẻo thơm, ăn kèm dừa sấy.",
            "30000", COLOR_DESSERT, true);
        createItem(dessert, "Sữa Chua Dĩa Trái Cây",
            "Sữa chua dĩa trộn dâu, xoài, chuối và mật ong.",
            "45000", COLOR_DESSERT, true);

        logger.info("=========================================================");
        logger.info(" Seeded menu: {} categories, {} dishes.",
            categoryRepository.countByDeletedAtIsNull(), menuItemRepository.countByDeletedAtIsNull());
        logger.info(" - Categories: Khai vị, Món chính, Lẩu & Nướng, Tráng miệng & Đồ uống");
        logger.info(" - Ảnh minh hoạ được tạo trong thư mục lưu ảnh cục bộ.");
        logger.info("=========================================================");
    }

    private Category createCategory(String name, int displayOrder) {
        return categoryRepository.save(new Category(UUID.randomUUID().toString(), name, displayOrder));
    }

    private void createItem(Category category, String name, String description, String price,
                            String placeholderColor, boolean available) {
        String id = UUID.randomUUID().toString();
        // Ảnh minh hoạ sinh tự động: ưu tiên đưa lên Cloudinary, không có thì lưu cục bộ.
        String imageUrl = imageStorageService.storePlaceholder(
                MENU_DIRECTORY, id + ".svg", name, placeholderColor).url();

        MenuItem item = new MenuItem(
            id,
            category.getId(),
            name,
            description,
            new BigDecimal(price),
            imageUrl,
            available
        );
        item.setCategory(category);
        menuItemRepository.save(item);
    }
}
