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

import java.time.Year;
import java.util.List;

/**
 * Fills the hour meter and, for machines rented with an operator, the operator sheet of the demo
 * catalogue. It runs after the catalogue seeders and only touches machines without hours, so databases
 * loaded before these fields existed get them too, and it never overwrites what an owner typed.
 */
@Component
@ConditionalOnProperty(name = "rentequip.demo-data.enabled", havingValue = "true")
@Order(3)
public class DemoEquipmentDetailsSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoEquipmentDetailsSeeder.class);

    /** Typical use of rental machines in Peru: about 1,100 hours a year. */
    private static final int HOURS_PER_YEAR = 1_100;

    private static final List<String> OPERATORS = List.of(
            "Juan Carlos Quispe", "Luis Alberto Mamani", "Pedro Huamán Ríos", "Wilber Condori Apaza",
            "Raúl Ccahuana Flores", "Edwin Ticona Vargas", "Marco Antonio Salas", "Jhon Pari Choque");

    private static final List<String> CERTIFICATIONS = List.of(
            "SCTR vigente\nCurso de seguridad en obra (40 h)",
            "SCTR vigente\nCertificado de operador de maquinaria pesada (SENCICO)",
            "SCTR vigente\nCapacitación del fabricante\nPrimeros auxilios");

    private final EquipmentRepository equipmentRepository;

    public DemoEquipmentDetailsSeeder(EquipmentRepository equipmentRepository) {
        this.equipmentRepository = equipmentRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Equipment> missing = equipmentRepository.findAll().stream()
                .filter(equipment -> equipment.getUsageHours() == null)
                .toList();
        int currentYear = Year.now().getValue();
        for (Equipment equipment : missing) {
            int seed = Math.toIntExact(equipment.getId() % 97);
            int age = equipment.getManufactureYear() == null ? 3 : Math.max(0, currentYear - equipment.getManufactureYear());
            equipment.setUsageHours(age * HOURS_PER_YEAR + seed * 37 % 900 + 120);
            if (equipment.isOperatorIncluded() && equipment.getOperatorName() == null) {
                equipment.setOperatorName(OPERATORS.get(seed % OPERATORS.size()));
                equipment.setOperatorLicense("A-IIIc");
                equipment.setOperatorExperienceYears(4 + seed % 12);
                equipment.setOperatorCertifications(CERTIFICATIONS.get(seed % CERTIFICATIONS.size()));
            }
        }
        if (!missing.isEmpty()) {
            log.info("Demo catalogue: filled hours of use for {} machines", missing.size());
        }
        // Demo owners stand for companies with their papers in order; their RUCs are not real, so the
        // badge is set here and the status says it was not checked with SUNAT.
        equipmentRepository.findAll().stream()
                .map(Equipment::getOwner)
                .distinct()
                .filter(owner -> !owner.isVerified() && owner.getTaxStatus() == null)
                .forEach(owner -> {
                    owner.setVerified(true);
                    owner.setVerifiedAt(java.time.LocalDateTime.now());
                    owner.setTaxStatus("DEMO (sin consulta a SUNAT)");
                });
    }
}
