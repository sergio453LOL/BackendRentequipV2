package com.rentequip.backend.config;

import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.repositories.EquipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gives ten machines of the demo catalogue a gallery: two more photos of the same model (or the same
 * family of the brand) after the one they were created with, so the detail page shows how browsing several
 * photos works. Only the three demo owners' units get them, and only while they still have their single
 * seeded photo: it runs on databases loaded before the galleries existed and never touches a machine whose
 * owner changed its photos. Authors and licences are in demo-images/CREDITS.md. The same ten also get a
 * YouTube video of their model.
 */
@Component
@ConditionalOnProperty(name = "rentequip.demo-data.enabled", havingValue = "true")
@Order(5)
public class DemoGallerySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoGallerySeeder.class);

    private static final String PREFIX = "/demo-images/";

    /** RUCs of the companies DemoDataSeeder creates (Maquinarias Andinas, Equipos del Sur, Herramientas Pro). */
    private static final Set<String> DEMO_OWNERS = Set.of("20601234561", "20456789012", "20512345673");

    /** Seeded photo → the extra photos that follow it in the gallery. */
    static final Map<String, List<String>> GALLERIES = Map.of(
            "excavadora-caterpillar-320.jpg", List.of("excavadora-caterpillar-320-2.jpg", "excavadora-caterpillar-320-3.jpg"),
            "excavadora-komatsu-pc200.jpg", List.of("excavadora-komatsu-pc200-2.jpg", "excavadora-komatsu-pc200-3.jpg"),
            "retroexcavadora-jcb-3cx.jpg", List.of("retroexcavadora-jcb-3cx-2.jpg", "retroexcavadora-jcb-3cx-3.jpg"),
            "minicargador-bobcat-s650.jpg", List.of("minicargador-bobcat-s650-2.jpg", "minicargador-bobcat-s650-3.jpg"),
            "rodillo-dynapac-ca151.jpg", List.of("rodillo-dynapac-ca151-2.jpg", "rodillo-dynapac-ca151-3.jpg"),
            "telehandler-jcb-540.jpg", List.of("telehandler-jcb-540-2.jpg", "telehandler-jcb-540-3.jpg"),
            "plancha-wacker-dpu6555.jpg", List.of("plancha-wacker-dpu6555-2.jpg", "plancha-wacker-dpu6555-3.jpg"),
            "andamio-layher.jpg", List.of("andamio-layher-2.jpg", "andamio-layher-3.jpg"),
            "rotomartillo-makita-hr3210.jpg", List.of("rotomartillo-makita-hr3210-2.jpg", "rotomartillo-makita-hr3210-3.jpg"),
            "taladro-dewalt-dcd985.jpg", List.of("taladro-dewalt-dcd985-2.jpg", "taladro-dewalt-dcd985-3.jpg"));

    /**
     * Seeded photo → a YouTube video of the same model, from the maker or a dealer (checked to exist and to
     * allow embedding). Set only while the machine has no video, so an owner's own video is never replaced.
     */
    static final Map<String, String> VIDEOS = Map.of(
            "excavadora-caterpillar-320.jpg", "https://www.youtube.com/watch?v=8xu5BmSjDD8",
            "excavadora-komatsu-pc200.jpg", "https://www.youtube.com/watch?v=4XNSYHE5voE",
            "retroexcavadora-jcb-3cx.jpg", "https://www.youtube.com/watch?v=oCJ_dbdOlm8",
            "minicargador-bobcat-s650.jpg", "https://www.youtube.com/watch?v=s62nrl78EjQ",
            "rodillo-dynapac-ca151.jpg", "https://www.youtube.com/watch?v=4fc8ZV7_jlk",
            "telehandler-jcb-540.jpg", "https://www.youtube.com/watch?v=HaR3GcKLcQY",
            "plancha-wacker-dpu6555.jpg", "https://www.youtube.com/watch?v=D1-ZfGjPCvQ",
            "andamio-layher.jpg", "https://www.youtube.com/watch?v=l_3U5YqG8PQ",
            "rotomartillo-makita-hr3210.jpg", "https://www.youtube.com/watch?v=hKy0rS3xs7A",
            "taladro-dewalt-dcd985.jpg", "https://www.youtube.com/watch?v=23aayTYJ3Bo");

    private final EquipmentRepository equipmentRepository;

    public DemoGallerySeeder(EquipmentRepository equipmentRepository) {
        this.equipmentRepository = equipmentRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Equipment> pending = equipmentRepository.findAll().stream()
                .filter(equipment -> DEMO_OWNERS.contains(equipment.getOwner().getTaxId()))
                .filter(equipment -> equipment.getImageUrls().size() == 1)
                .filter(equipment -> GALLERIES.containsKey(seededPhoto(equipment)))
                .toList();
        for (Equipment equipment : pending) {
            GALLERIES.get(seededPhoto(equipment)).forEach(name -> equipment.getImageUrls().add(PREFIX + name));
        }
        if (!pending.isEmpty()) {
            log.info("Demo catalogue: added a photo gallery to {} machines", pending.size());
        }

        List<Equipment> withoutVideo = equipmentRepository.findAll().stream()
                .filter(equipment -> DEMO_OWNERS.contains(equipment.getOwner().getTaxId()))
                .filter(equipment -> equipment.getVideoUrl() == null || equipment.getVideoUrl().isBlank())
                .filter(equipment -> !equipment.getImageUrls().isEmpty() && VIDEOS.containsKey(seededPhoto(equipment)))
                .toList();
        withoutVideo.forEach(equipment -> equipment.setVideoUrl(VIDEOS.get(seededPhoto(equipment))));
        if (!withoutVideo.isEmpty()) {
            log.info("Demo catalogue: added a demo video to {} machines", withoutVideo.size());
        }
    }

    /** File name of the single seeded photo ("excavadora-caterpillar-320.jpg"), or "" for an uploaded one. */
    private static String seededPhoto(Equipment equipment) {
        String url = equipment.getImageUrls().getFirst();
        return url.startsWith(PREFIX) ? url.substring(PREFIX.length()) : "";
    }
}
