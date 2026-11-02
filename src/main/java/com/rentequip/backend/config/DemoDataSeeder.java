package com.rentequip.backend.config;

import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.EquipmentCategory;
import com.rentequip.backend.entities.User;
import com.rentequip.backend.enums.UserRole;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.repositories.EquipmentCategoryRepository;
import com.rentequip.backend.repositories.EquipmentRepository;
import com.rentequip.backend.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a demo catalogue of heavy machinery and construction tools, with real brands, models and photos,
 * so the platform never starts empty in a demo. It only runs with DEMO_DATA_ENABLED=true and only once:
 * if the first demo company already exists, it does nothing.
 *
 * <p>The owner companies get an administrator only when DEMO_PASSWORD is set, so no password ever lives
 * in the repository.
 */
@Component
@ConditionalOnProperty(name = "rentequip.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** General tag on every hand tool, on top of its specific category, so tools can be counted and filtered. */
    private static final String TOOLS = "Herramientas";

    private record CompanySeed(String key, String name, String taxId, String email, String address, String city,
                               String latitude, String longitude) {
    }

    private record CategorySeed(String name, String description) {
    }

    private record EquipmentSeed(String owner, List<String> categories, String name, String brand, String model, int year,
                                 String description, String specifications, String dailyRate, String weeklyRate,
                                 String deposit, String image) {
    }

    private static final List<CompanySeed> COMPANIES = List.of(
            new CompanySeed("andinas", "Maquinarias Andinas SAC", "20601234561", "contacto@maquinariasandinas.demo",
                    "Av. Nicolás Ayllón 4520, Ate", "Lima", "-12.0553", "-76.9372"),
            new CompanySeed("sur", "Equipos del Sur EIRL", "20456789012", "ventas@equiposdelsur.demo",
                    "Parque Industrial, Calle 4", "Arequipa", "-16.4180", "-71.5160"),
            new CompanySeed("herramientas", "Herramientas Pro Perú SAC", "20512345673", "alquiler@herramientaspro.demo",
                    "Av. Tomás Marsano 2875, Surco", "Lima", "-12.1270", "-77.0040"));

    private static final List<CategorySeed> CATEGORIES = List.of(
            new CategorySeed(TOOLS, "Herramientas y equipos menores que se llevan a mano o en camioneta"),
            new CategorySeed("Excavadoras", "Excavadoras hidráulicas sobre orugas"),
            new CategorySeed("Retroexcavadoras", "Retroexcavadoras y cargadores retro"),
            new CategorySeed("Minicargadores", "Minicargadores y cargadores compactos"),
            new CategorySeed("Compactación", "Rodillos, planchas y pisones compactadores"),
            new CategorySeed("Elevación", "Manipuladores telescópicos y equipos de izaje"),
            new CategorySeed("Generadores", "Grupos electrógenos para obra"),
            new CategorySeed("Andamios", "Andamios y estructuras de acceso"),
            new CategorySeed("Herramientas eléctricas", "Taladros, rotomartillos y herramientas de perforación"),
            new CategorySeed("Demolición", "Martillos demoledores y rompepavimentos"),
            new CategorySeed("Concreto", "Vibradores y equipos para vaciado de concreto"));

    private static final List<EquipmentSeed> EQUIPMENT = List.of(
            new EquipmentSeed("andinas", List.of("Excavadoras"), "Excavadora Caterpillar 320", "Caterpillar", "320", 2019,
                    "Excavadora de 22 t para movimiento de tierras, zanjas y cimentaciones. Se entrega con operador opcional.",
                    "Peso operativo: 22 t\nPotencia: 121 kW\nProfundidad máxima de excavación: 6.7 m\nCucharón: 1.2 m³",
                    "1350", "8100", "3000", "excavadora-caterpillar-320.jpg"),
            new EquipmentSeed("sur", List.of("Excavadoras"), "Excavadora Komatsu PC200-8", "Komatsu", "PC200-8", 2018,
                    "Excavadora versátil para obras urbanas y canteras, con bajo consumo de combustible.",
                    "Peso operativo: 20 t\nPotencia: 110 kW\nProfundidad máxima de excavación: 6.6 m\nCucharón: 0.8 m³",
                    "1200", "7200", "2500", "excavadora-komatsu-pc200.jpg"),
            new EquipmentSeed("andinas", List.of("Retroexcavadoras"), "Retroexcavadora JCB 3CX", "JCB", "3CX", 2020,
                    "Retroexcavadora con cargador frontal, ideal para redes de agua y desagüe y trabajos en ciudad.",
                    "Peso operativo: 8.1 t\nPotencia: 74 kW\nProfundidad de excavación: 5.9 m\nCucharón frontal: 1 m³",
                    "750", "4500", "1500", "retroexcavadora-jcb-3cx.jpg"),
            new EquipmentSeed("sur", List.of("Minicargadores"), "Minicargador Bobcat S650", "Bobcat", "S650", 2021,
                    "Minicargador compacto para espacios reducidos: carga, limpieza de obra y nivelación.",
                    "Carga operativa: 1,250 kg\nPotencia: 55 kW\nCucharón: 0.5 m³",
                    "520", "3100", "1000", "minicargador-bobcat-s650.jpg"),
            new EquipmentSeed("andinas", List.of("Compactación"), "Rodillo compactador Dynapac CA151", "Dynapac", "CA151", 2016,
                    "Rodillo vibratorio de tambor liso para compactar bases, subbases y rellenos.",
                    "Peso operativo: 7 t\nAncho del tambor: 1.68 m\nFrecuencia de vibración: 33 Hz",
                    "650", "3900", "1500", "rodillo-dynapac-ca151.jpg"),
            new EquipmentSeed("sur", List.of("Elevación"), "Manipulador telescópico JCB 540-170", "JCB", "540-170", 2019,
                    "Telehandler para subir materiales a pisos altos y descargar camiones en obra.",
                    "Capacidad de carga: 4 t\nAltura máxima de elevación: 17 m\nTracción 4x4",
                    "950", "5700", "2500", "telehandler-jcb-540.jpg"),
            new EquipmentSeed("andinas", List.of("Generadores"), "Grupo electrógeno Caterpillar Olympian D200", "Caterpillar", "Olympian D200", 2017,
                    "Generador diésel insonorizado para dar energía a la obra o como respaldo.",
                    "Potencia: 200 kVA\nCombustible: diésel\nCabina insonorizada",
                    "480", "2880", "1200", "generador-caterpillar.jpg"),
            new EquipmentSeed("herramientas", List.of("Andamios"), "Andamio multidireccional Layher (kit 100 m²)", "Layher", "Allround", 2022,
                    "Kit de andamio galvanizado para fachadas y encofrados. Incluye plataformas, barandas y rodapiés.",
                    "Material: acero galvanizado\nCobertura: 100 m² de fachada\nAltura máxima: 20 m",
                    "180", "1080", "500", "andamio-layher.jpg"),
            new EquipmentSeed("herramientas", List.of(TOOLS, "Herramientas eléctricas"), "Rotomartillo Makita HR3210FCT", "Makita", "HR3210FCT", 2021,
                    "Rotomartillo SDS-Plus con sistema antivibración para perforar y picar concreto.",
                    "Potencia: 850 W\nEnergía de impacto: 4.9 J\nPerforación en concreto: hasta 32 mm",
                    "45", "270", "200", "rotomartillo-makita-hr3210.jpg"),
            new EquipmentSeed("herramientas", List.of(TOOLS, "Herramientas eléctricas"), "Taladro percutor inalámbrico DeWalt DCD985", "DeWalt", "DCD985", 2022,
                    "Taladro percutor de 18 V para concreto, ladrillo, madera y metal. Incluye 2 baterías y cargador.",
                    "Voltaje: 18 V XRP\nTorque: 80 Nm\nBaterías: 2 x 3 Ah",
                    "35", "210", "150", "taladro-dewalt-dcd985.jpg"),
            new EquipmentSeed("herramientas", List.of(TOOLS, "Demolición"), "Martillo demoledor eléctrico 15 kg", "Genérico", "Demoledor 1500 W", 2020,
                    "Martillo demoledor hexagonal para romper pisos, muros y losas de concreto.",
                    "Potencia: 1,500 W\nPeso: 15 kg\nEnergía de impacto: 45 J\nIncluye puntero y cincel",
                    "90", "540", "350", "martillo-demoledor.jpg"),
            new EquipmentSeed("sur", List.of(TOOLS, "Demolición"), "Rompepavimentos neumático con compresora", "Genérico", "Kit neumático 25 kg", 2018,
                    "Martillo neumático de 25 kg con compresora remolcable para romper pistas y veredas.",
                    "Martillo: 25 kg\nCompresora: 185 CFM, remolcable\nIncluye mangueras y puntas",
                    "320", "1920", "900", "rompepavimentos.jpg"),
            new EquipmentSeed("herramientas", List.of(TOOLS, "Compactación"), "Plancha compactadora Wacker Neuson DPU 6555", "Wacker Neuson", "DPU 6555", 2020,
                    "Plancha reversible diésel para compactar suelos, adoquines y zanjas.",
                    "Peso: 500 kg\nFuerza centrífuga: 65 kN\nMotor: diésel\nReversible",
                    "220", "1320", "600", "plancha-wacker-dpu6555.jpg"),
            new EquipmentSeed("herramientas", List.of(TOOLS, "Concreto"), "Vibrador de concreto eléctrico", "Genérico", "Inmersión 2300 W", 2021,
                    "Vibrador de inmersión para eliminar burbujas de aire en columnas, vigas y losas.",
                    "Potencia: 2,300 W\nAguja: 38 mm\nManguera: 4 m",
                    "40", "240", "150", "vibrador-concreto.jpg"));

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final EquipmentCategoryRepository categoryRepository;
    private final EquipmentRepository equipmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoDataSeeder(CompanyRepository companyRepository, UserRepository userRepository,
                          EquipmentCategoryRepository categoryRepository, EquipmentRepository equipmentRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${rentequip.demo-data.password:}") String demoPassword) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.equipmentRepository = equipmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (companyRepository.existsByTaxId(COMPANIES.getFirst().taxId())) {
            log.info("Demo catalogue already loaded");
            return;
        }

        Map<String, EquipmentCategory> categories = new HashMap<>();
        for (CategorySeed seed : CATEGORIES) {
            categories.put(seed.name(), categoryRepository.findByNameIgnoreCase(seed.name())
                    .orElseGet(() -> categoryRepository.save(newCategory(seed))));
        }

        Map<String, Company> companies = new HashMap<>();
        for (CompanySeed seed : COMPANIES) {
            Company company = newCompany(seed);
            if (!demoPassword.isBlank()) {
                company.addUser(newAdministrator(seed));
            }
            companies.put(seed.key(), companyRepository.save(company));
        }

        for (int index = 0; index < EQUIPMENT.size(); index++) {
            EquipmentSeed seed = EQUIPMENT.get(index);
            Company owner = companies.get(seed.owner());
            Equipment equipment = newEquipment(seed, owner, index);
            seed.categories().forEach(name -> equipment.addCategory(categories.get(name)));
            owner.addEquipment(equipment);
            equipmentRepository.save(equipment);
        }
        log.info("Demo catalogue loaded: {} companies, {} machines and tools{}", COMPANIES.size(), EQUIPMENT.size(),
                demoPassword.isBlank() ? " (no owner logins: DEMO_PASSWORD is not set)" : "");
    }

    private EquipmentCategory newCategory(CategorySeed seed) {
        EquipmentCategory category = new EquipmentCategory();
        category.setName(seed.name());
        category.setDescription(seed.description());
        return category;
    }

    private Company newCompany(CompanySeed seed) {
        Company company = new Company();
        company.setName(seed.name());
        company.setTaxId(seed.taxId());
        company.setEmail(seed.email());
        company.setAddress(seed.address());
        company.setCity(seed.city());
        company.setLatitude(new BigDecimal(seed.latitude()));
        company.setLongitude(new BigDecimal(seed.longitude()));
        return company;
    }

    private User newAdministrator(CompanySeed seed) {
        User user = new User();
        user.setFirstName("Admin");
        user.setLastName(seed.name());
        user.setEmail("admin@" + seed.email().substring(seed.email().indexOf('@') + 1));
        user.setPassword(passwordEncoder.encode(demoPassword));
        user.setRole(UserRole.ADMIN);
        return user;
    }

    /** Each machine sits a few hundred metres from its owner's yard, so the map pins do not stack. */
    private Equipment newEquipment(EquipmentSeed seed, Company owner, int index) {
        BigDecimal offset = new BigDecimal("0.004").multiply(BigDecimal.valueOf(index % 5 - 2));
        Equipment equipment = new Equipment();
        equipment.setName(seed.name());
        equipment.setBrand(seed.brand());
        equipment.setModel(seed.model());
        equipment.setManufactureYear(seed.year());
        equipment.setDescription(seed.description());
        equipment.setSpecifications(seed.specifications());
        equipment.setDailyRate(new BigDecimal(seed.dailyRate()));
        equipment.setWeeklyRate(new BigDecimal(seed.weeklyRate()));
        equipment.setSecurityDeposit(new BigDecimal(seed.deposit()));
        equipment.setCurrency("PEN");
        equipment.setAddress(owner.getAddress());
        equipment.setCity(owner.getCity());
        equipment.setLatitude(owner.getLatitude().add(offset));
        equipment.setLongitude(owner.getLongitude().subtract(offset.multiply(new BigDecimal("0.7"))));
        equipment.getImageUrls().add("/demo-images/" + seed.image());
        return equipment;
    }
}
