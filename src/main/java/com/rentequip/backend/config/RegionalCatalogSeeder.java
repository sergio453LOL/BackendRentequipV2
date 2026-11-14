package com.rentequip.backend.config;

import com.rentequip.backend.config.DemoDataSeeder.EquipmentSeed;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.EquipmentCategory;
import com.rentequip.backend.entities.User;
import com.rentequip.backend.enums.UserRole;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.repositories.EquipmentCategoryRepository;
import com.rentequip.backend.repositories.EquipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Spreads the demo catalogue across Peru: sixteen rental companies in the regions, each with twelve
 * machines and tools built from the base catalogue's models and photos, with their own year, rates and
 * location. It runs after {@link DemoDataSeeder} (it needs its categories), with the same flag, and only
 * once: if the first regional company exists, it does nothing.
 *
 * <p>Everything is derived from the company and item index, so every fresh database gets the same catalogue.
 */
@Component
@ConditionalOnProperty(name = "rentequip.demo-data.enabled", havingValue = "true")
@Order(2)
public class RegionalCatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegionalCatalogSeeder.class);

    private static final int ITEMS_PER_COMPANY = 12;
    private static final int NEWEST_YEAR = 2025;

    private record RegionalCompany(String name, String taxId, String domain, String address, String city,
                                   String latitude, String longitude) {
    }

    private static final List<RegionalCompany> COMPANIES = List.of(
            new RegionalCompany("Maquinarias del Norte SAC", "20600111001", "maquinariasdelnorte.demo",
                    "Av. Nicolás de Piérola 1240", "Trujillo", "-8.1116", "-79.0288"),
            new RegionalCompany("Equipos Lambayeque EIRL", "20600111002", "equiposlambayeque.demo",
                    "Av. Augusto B. Leguía 980", "Chiclayo", "-6.7714", "-79.8409"),
            new RegionalCompany("Alquileres Piura SAC", "20600111003", "alquilerespiura.demo",
                    "Zona Industrial, Mz. B Lt. 7", "Piura", "-5.1945", "-80.6328"),
            new RegionalCompany("Inka Maquinaria SAC", "20600111004", "inkamaquinaria.demo",
                    "Av. de la Cultura 2150", "Cusco", "-13.5320", "-71.9675"),
            new RegionalCompany("Centro Andino Equipos SAC", "20600111005", "centroandino.demo",
                    "Av. Mariscal Castilla 3310, El Tambo", "Huancayo", "-12.0651", "-75.2049"),
            new RegionalCompany("Cajamarca Rental EIRL", "20600111006", "cajamarcarental.demo",
                    "Av. Vía de Evitamiento Norte 1520", "Cajamarca", "-7.1638", "-78.5003"),
            new RegionalCompany("Equipos del Valle Ica SAC", "20600111007", "equiposdelvalle.demo",
                    "Panamericana Sur km 303", "Ica", "-14.0678", "-75.7286"),
            new RegionalCompany("Frontera Sur Maquinarias SAC", "20600111008", "fronterasur.demo",
                    "Parque Industrial de Tacna, Mz. D", "Tacna", "-18.0146", "-70.2536"),
            new RegionalCompany("Altiplano Equipos EIRL", "20600111009", "altiplanoequipos.demo",
                    "Av. Circunvalación Este 845", "Puno", "-15.8402", "-70.0219"),
            new RegionalCompany("Amazonía Rental SAC", "20600111010", "amazoniarental.demo",
                    "Av. Participación 1130", "Iquitos", "-3.7491", "-73.2538"),
            new RegionalCompany("Huamanga Maquinarias SAC", "20600111011", "huamangamaquinarias.demo",
                    "Av. Cusco 560", "Ayacucho", "-13.1631", "-74.2236"),
            new RegionalCompany("Cordillera Blanca Equipos EIRL", "20600111012", "cordillerablanca.demo",
                    "Av. Centenario 1890", "Huaraz", "-9.5278", "-77.5278"),
            new RegionalCompany("Ucayali Maquinaria SAC", "20600111013", "ucayalimaquinaria.demo",
                    "Carretera Federico Basadre km 4.5", "Pucallpa", "-8.3791", "-74.5539"),
            new RegionalCompany("Puerto Chimbote Rental SAC", "20600111014", "chimboterental.demo",
                    "Av. Industrial 410", "Chimbote", "-9.0745", "-78.5936"),
            new RegionalCompany("Callao Port Equipos SAC", "20600111015", "callaoport.demo",
                    "Av. Néstor Gambetta 6230", "Callao", "-12.0566", "-77.1181"),
            new RegionalCompany("Sur Chico Maquinarias EIRL", "20600111016", "surchico.demo",
                    "Antigua Panamericana Sur km 32, Lurín", "Lima", "-12.2746", "-76.8706"));

    private final CompanyRepository companyRepository;
    private final EquipmentCategoryRepository categoryRepository;
    private final EquipmentRepository equipmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public RegionalCatalogSeeder(CompanyRepository companyRepository, EquipmentCategoryRepository categoryRepository,
                                 EquipmentRepository equipmentRepository, PasswordEncoder passwordEncoder,
                                 @Value("${rentequip.demo-data.password:}") String demoPassword) {
        this.companyRepository = companyRepository;
        this.categoryRepository = categoryRepository;
        this.equipmentRepository = equipmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (companyRepository.existsByTaxId(COMPANIES.getFirst().taxId())) {
            log.info("Regional demo catalogue already loaded");
            return;
        }

        List<EquipmentSeed> models = DemoDataSeeder.EQUIPMENT;
        int created = 0;
        for (int companyIndex = 0; companyIndex < COMPANIES.size(); companyIndex++) {
            RegionalCompany seed = COMPANIES.get(companyIndex);
            Company company = newCompany(seed);
            if (!demoPassword.isBlank()) {
                company.addUser(newAdministrator(seed));
            }
            companyRepository.save(company);

            for (int item = 0; item < ITEMS_PER_COMPANY; item++) {
                // Each company starts at a different model, so neighbouring regions do not list the same fleet.
                EquipmentSeed model = models.get((companyIndex * 5 + item) % models.size());
                Equipment equipment = newEquipment(model, company, companyIndex, item);
                for (String name : model.categories()) {
                    EquipmentCategory category = categoryRepository.findByNameIgnoreCase(name).orElse(null);
                    if (category != null) {
                        equipment.addCategory(category);
                    }
                }
                company.addEquipment(equipment);
                equipmentRepository.save(equipment);
                created++;
            }
        }
        log.info("Regional demo catalogue loaded: {} companies, {} machines and tools", COMPANIES.size(), created);
    }

    private Company newCompany(RegionalCompany seed) {
        Company company = new Company();
        company.setName(seed.name());
        company.setTaxId(seed.taxId());
        company.setEmail("contacto@" + seed.domain());
        company.setAddress(seed.address());
        company.setCity(seed.city());
        company.setLatitude(new BigDecimal(seed.latitude()));
        company.setLongitude(new BigDecimal(seed.longitude()));
        return company;
    }

    private User newAdministrator(RegionalCompany seed) {
        User user = new User();
        user.setFirstName("Admin");
        user.setLastName(seed.name());
        user.setEmail("admin@" + seed.domain());
        user.setPassword(passwordEncoder.encode(demoPassword));
        user.setRole(UserRole.ADMIN);
        return user;
    }

    /**
     * A copy of a base model with this company's own details: rates move with the region (±15%) and the unit
     * (±8%), the year by up to two, and the pin sits on a small grid around the company's yard.
     */
    private Equipment newEquipment(EquipmentSeed model, Company owner, int companyIndex, int item) {
        BigDecimal factor = BigDecimal.valueOf(0.85 + (companyIndex % 7) * 0.05)
                .multiply(BigDecimal.valueOf(1 + ((item * 13 % 9) - 4) * 0.02));
        BigDecimal dailyRate = scaled(model.dailyRate(), factor);
        BigDecimal latitudeOffset = new BigDecimal("0.006").multiply(BigDecimal.valueOf(item % 4 - 1.5));
        BigDecimal longitudeOffset = new BigDecimal("0.006").multiply(BigDecimal.valueOf(item / 4 - 1));

        Equipment equipment = new Equipment();
        equipment.setName(model.name());
        equipment.setBrand(model.brand());
        equipment.setModel(model.model());
        equipment.setManufactureYear(Math.min(NEWEST_YEAR, model.year() + (companyIndex + item) % 5 - 2));
        equipment.setDescription(model.description() + " Disponible en " + owner.getCity() + " y alrededores.");
        equipment.setSpecifications(model.specifications());
        equipment.setDailyRate(dailyRate);
        equipment.setWeeklyRate(scaled(model.weeklyRate(), factor));
        equipment.setSecurityDeposit(scaled(model.deposit(), factor));
        equipment.setCurrency("PEN");
        equipment.setAddress(owner.getAddress());
        equipment.setCity(owner.getCity());
        equipment.setLatitude(owner.getLatitude().add(latitudeOffset));
        equipment.setLongitude(owner.getLongitude().add(longitudeOffset));
        equipment.getImageUrls().add("/demo-images/" + model.image());
        equipment.setOperatorIncluded(model.categories().stream().anyMatch(DemoDataSeeder.OPERATED_CATEGORIES::contains));
        return equipment;
    }

    /** Rounded to S/ 5 below S/ 100 and to S/ 10 above, like a real price list. */
    private static BigDecimal scaled(String amount, BigDecimal factor) {
        BigDecimal value = new BigDecimal(amount).multiply(factor);
        BigDecimal step = value.compareTo(BigDecimal.valueOf(100)) < 0 ? BigDecimal.valueOf(5) : BigDecimal.TEN;
        return value.divide(step, 0, RoundingMode.HALF_UP).multiply(step).setScale(2, RoundingMode.UNNECESSARY);
    }
}
