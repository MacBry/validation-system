package com.mac.bry.validationsystem.materialtype;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MaterialTypeDataInitializer implements CommandLineRunner {

    private final MaterialTypeRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() == 0) {
            log.info("Inicjalizacja domyślnych typów materiałów...");

            List<MaterialType> defaults = Arrays.asList(
                    create("Krew pełna i składniki krwi", "83.14", "WHO Technical Report Series 961 (2011)",
                            "RCKiK, banki krwi", 2.0, 6.0),
                    create("Osocze", "83.14", "WHO TRS 961", "Banki krwi", -30.0, -20.0),
                    create("Koncentrat krwinek czerwonych (KKCz)", "83.14", "WHO TRS 961", "Banki krwi", 2.0, 6.0),
                    create("Koncentrat płytek krwi (KPC)", "83.14", "WHO TRS 961", "Banki krwi", 20.0, 24.0),
                    create("Szczepionki", "83.14", "WHO TRS 961, ICH Q1A", "Magazyny szczepionek", 2.0, 8.0),
                    create("Leki białkowe (insulina, przeciwciała)", "83.14", "ICH Q1A(R2)", "Apteki szpitalne", 2.0,
                            8.0),
                    create("Leki standardowe (tabletki, kapsułki)", "70.00", "ICH Q1A(R2)", "Apteki, hurtownie", 15.0,
                            25.0),
                    create("Leki termolabilne", "100.00", "ICH Q1A(R2)", "Produkty wrażliwe", 2.0, 8.0),
                    create("Odczynniki diagnostyczne", "65.00", "ISO 15189", "Laboratoria medyczne", 2.0, 8.0),
                    create("Próbki biologiczne (DNA, RNA)", "83.14", "Praktyka laboratoryjna", "Biobanki, laboratoria",
                            -80.0, -20.0),
                    create("Probiotyki", "83.14", "Praktyka farmaceutyczna", "Żywność funkcjonalna", 2.0, 8.0),
                    create("Surowce farmaceutyczne (API)", "70.00", "ICH Q1A(R2)", "Produkcja farmaceutyczna", 15.0,
                            25.0));

            repository.saveAll(defaults);
            log.info("Zakończono inicjalizację domyślnych typów materiałów ({} pozycji).", defaults.size());
        }
    }

    private MaterialType create(String name, String energy, String source, String app, Double min, Double max) {
        return MaterialType.builder()
                .name(name)
                .activationEnergy(new BigDecimal(energy))
                .standardSource(source)
                .application(app)
                .minStorageTemp(min)
                .maxStorageTemp(max)
                .active(true)
                .build();
    }
}
