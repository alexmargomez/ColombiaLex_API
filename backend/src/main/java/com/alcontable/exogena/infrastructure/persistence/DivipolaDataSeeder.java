package com.alcontable.exogena.infrastructure.persistence;

import com.alcontable.exogena.domain.model.MunicipalityEntity;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DivipolaDataSeeder implements CommandLineRunner {

    private final MunicipalityJpaRepository repository;

    public DivipolaDataSeeder(MunicipalityJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }

        List<MunicipalityEntity> seed = List.of(
                new MunicipalityEntity("11001", "11", "Bogotá, D.C.", "Bogotá, D.C.", "ESPECIAL", "Región Andina", 4.7110, -74.0721, true),
                new MunicipalityEntity("05001", "05", "Antioquia", "Medellín", "ESPECIAL", "Región Andina", 6.2442, -75.5812, true),
                new MunicipalityEntity("05088", "05", "Antioquia", "Bello", "PRIMERA", "Región Andina", 6.3373, -75.5579, false),
                new MunicipalityEntity("05266", "05", "Antioquia", "Envigado", "PRIMERA", "Región Andina", 6.1696, -75.5875, false),
                new MunicipalityEntity("05360", "05", "Antioquia", "Itagüí", "PRIMERA", "Región Andina", 6.1726, -75.6096, false),
                new MunicipalityEntity("05615", "05", "Antioquia", "Rionegro", "PRIMERA", "Región Andina", 6.1552, -75.3737, false),
                new MunicipalityEntity("76001", "76", "Valle del Cauca", "Cali", "ESPECIAL", "Región Pacífica", 3.4516, -76.5320, true),
                new MunicipalityEntity("76520", "76", "Valle del Cauca", "Palmira", "PRIMERA", "Región Pacífica", 3.5394, -76.3036, false),
                new MunicipalityEntity("76109", "76", "Valle del Cauca", "Buenaventura", "PRIMERA", "Región Pacífica", 3.8801, -77.0312, false),
                new MunicipalityEntity("08001", "08", "Atlántico", "Barranquilla", "ESPECIAL", "Región Caribe", 10.9685, -74.7813, true),
                new MunicipalityEntity("08758", "08", "Atlántico", "Soledad", "PRIMERA", "Región Caribe", 10.9184, -74.7676, false),
                new MunicipalityEntity("13001", "13", "Bolívar", "Cartagena de Indias", "ESPECIAL", "Región Caribe", 10.3910, -75.4794, true),
                new MunicipalityEntity("68001", "68", "Santander", "Bucaramanga", "ESPECIAL", "Región Andina", 7.1193, -73.1227, true),
                new MunicipalityEntity("68276", "68", "Santander", "Floridablanca", "PRIMERA", "Región Andina", 7.0622, -73.0864, false),
                new MunicipalityEntity("68307", "68", "Santander", "Girón", "SEGUNDA", "Región Andina", 7.0684, -73.1698, false),
                new MunicipalityEntity("68547", "68", "Santander", "Piedecuesta", "SEGUNDA", "Región Andina", 6.9879, -73.0501, false),
                new MunicipalityEntity("68081", "68", "Santander", "Barrancabermeja", "PRIMERA", "Región Andina", 7.0653, -73.8547, false),
                new MunicipalityEntity("68679", "68", "Santander", "San Gil", "TERCERA", "Región Andina", 6.5553, -73.1333, false),
                new MunicipalityEntity("68895", "68", "Santander", "Zapatoca", "SEXTA", "Región Andina", 6.8147, -73.2683, false),
                new MunicipalityEntity("54001", "54", "Norte de Santander", "Cúcuta", "PRIMERA", "Región Andina", 7.8939, -72.5078, true),
                new MunicipalityEntity("15001", "15", "Boyacá", "Tunja", "PRIMERA", "Región Andina", 5.5353, -73.3678, true),
                new MunicipalityEntity("15238", "15", "Boyacá", "Duitama", "SEGUNDA", "Región Andina", 5.8267, -73.0333, false),
                new MunicipalityEntity("15759", "15", "Boyacá", "Sogamoso", "SEGUNDA", "Región Andina", 5.7145, -72.9339, false),
                new MunicipalityEntity("17001", "17", "Caldas", "Manizales", "PRIMERA", "Región Andina", 5.0703, -75.5138, true),
                new MunicipalityEntity("66001", "66", "Risaralda", "Pereira", "PRIMERA", "Región Andina", 4.8133, -75.6961, true),
                new MunicipalityEntity("66170", "66", "Risaralda", "Dosquebradas", "SEGUNDA", "Región Andina", 4.8364, -75.6725, false),
                new MunicipalityEntity("63001", "63", "Quindío", "Armenia", "PRIMERA", "Región Andina", 4.5339, -75.6811, true),
                new MunicipalityEntity("73001", "73", "Tolima", "Ibagué", "PRIMERA", "Región Andina", 4.4389, -75.2322, true),
                new MunicipalityEntity("41001", "41", "Huila", "Neiva", "PRIMERA", "Región Andina", 2.9273, -75.2819, true),
                new MunicipalityEntity("50001", "50", "Meta", "Villavicencio", "PRIMERA", "Región Orinoquía", 4.1420, -73.6266, true),
                new MunicipalityEntity("85001", "85", "Casanare", "Yopal", "PRIMERA", "Región Orinoquía", 5.3378, -72.3959, true),
                new MunicipalityEntity("47001", "47", "Magdalena", "Santa Marta", "PRIMERA", "Región Caribe", 11.2408, -74.1990, true),
                new MunicipalityEntity("20001", "20", "Cesar", "Valledupar", "PRIMERA", "Región Caribe", 10.4631, -73.2532, true),
                new MunicipalityEntity("23001", "23", "Córdoba", "Montería", "PRIMERA", "Región Caribe", 8.7480, -75.8814, true),
                new MunicipalityEntity("70001", "70", "Sucre", "Sincelejo", "SEGUNDA", "Región Caribe", 9.3047, -75.3978, true),
                new MunicipalityEntity("44001", "44", "La Guajira", "Riohacha", "SEGUNDA", "Región Caribe", 11.5444, -72.9072, true),
                new MunicipalityEntity("88001", "88", "San Andrés y Providencia", "San Andrés", "ESPECIAL", "Región Insular", 12.5847, -81.7006, true),
                new MunicipalityEntity("19001", "19", "Cauca", "Popayán", "PRIMERA", "Región Pacífica", 2.4448, -76.6147, true),
                new MunicipalityEntity("52001", "52", "Nariño", "Pasto", "PRIMERA", "Región Pacífica", 1.2136, -77.2811, true),
                new MunicipalityEntity("27001", "27", "Chocó", "Quibdó", "TERCERA", "Región Pacífica", 5.6947, -76.6611, true),
                new MunicipalityEntity("25754", "25", "Cundinamarca", "Soacha", "PRIMERA", "Región Andina", 4.5794, -74.2168, false),
                new MunicipalityEntity("25175", "25", "Cundinamarca", "Chía", "PRIMERA", "Región Andina", 4.8616, -74.0325, false),
                new MunicipalityEntity("25899", "25", "Cundinamarca", "Zipaquirá", "SEGUNDA", "Región Andina", 5.0221, -74.0040, false),
                new MunicipalityEntity("18001", "18", "Caquetá", "Florencia", "SEGUNDA", "Región Amazónica", 1.6144, -75.6062, true),
                new MunicipalityEntity("91001", "91", "Amazonas", "Leticia", "CUARTA", "Región Amazónica", -4.2153, -69.9406, true)
        );

        repository.saveAll(seed);
    }
}
