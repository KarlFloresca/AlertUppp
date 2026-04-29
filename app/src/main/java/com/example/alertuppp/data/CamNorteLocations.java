package com.example.alertuppp.data;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete list of municipalities and barangays in Camarines Norte.
 * Source: PhilAtlas / PSGC 2020 (282 barangays, 12 municipalities).
 */
public class CamNorteLocations {

    /** Ordered list of all 12 municipalities. */
    public static final String[] MUNICIPALITIES = {
            "Basud", "Capalonga", "Daet", "Jose Panganiban",
            "Labo", "Mercedes", "Paracale", "San Lorenzo Ruiz",
            "San Vicente", "Santa Elena", "Talisay", "Vinzons"
    };

    /** Map of municipality → sorted barangay list. */
    public static final Map<String, List<String>> BARANGAYS = new LinkedHashMap<>();

    static {
        BARANGAYS.put("Basud", Arrays.asList(
                "Angas", "Bactas", "Binatagan", "Caayunan", "Guinatungan",
                "Hinampacan", "Langa", "Laniton", "Lidong", "Mampili",
                "Mandazo", "Mangcamagong", "Manmuntay", "Mantugawe", "Matnog",
                "Mocong", "Oliva", "Pagsangahan", "Pinagwarasan", "Plaridel",
                "Poblacion 1", "Poblacion 2", "San Felipe", "San Jose",
                "San Pascual", "Taba-taba", "Tacad", "Taisan", "Tuaca"
        ));

        BARANGAYS.put("Capalonga", Arrays.asList(
                "Alayao", "Binawangan", "Calabaca", "Camagsaan", "Catabaguangan",
                "Catioan", "Del Pilar", "Itok", "Lucbanan", "Mabini",
                "Mactang", "Magsaysay", "Mataque", "Old Camp", "Poblacion",
                "San Antonio", "San Isidro", "San Roque", "Tanawan", "Ubang",
                "Villa Aurora", "Villa Belen"
        ));

        BARANGAYS.put("Daet", Arrays.asList(
                "Alawihao", "Awitan", "Bagasbas", "Barangay I", "Barangay II",
                "Barangay III", "Barangay IV", "Barangay V", "Barangay VI",
                "Barangay VII", "Barangay VIII", "Bibirao", "Borabod",
                "Calasgasan", "Camambugan", "Cobangbang", "Dogongan",
                "Gahonon", "Gubat", "Lag-on", "Magang", "Mambalite",
                "Mancruz", "Pamorangon", "San Isidro"
        ));

        BARANGAYS.put("Jose Panganiban", Arrays.asList(
                "Bagong Bayan", "Calero", "Dahican", "Dayhagan", "Larap",
                "Luklukan Norte", "Luklukan Sur", "Motherlode", "Nakalaya",
                "North Poblacion", "Osmeña", "Pag-asa", "Parang", "Plaridel",
                "Salvacion", "San Isidro", "San Jose", "San Martin", "San Pedro",
                "San Rafael", "Santa Cruz", "Santa Elena", "Santa Milagrosa",
                "Santa Rosa Norte", "Santa Rosa Sur", "South Poblacion", "Tamisan"
        ));

        BARANGAYS.put("Labo", Arrays.asList(
                "Anahaw", "Anameam", "Awitan", "Baay", "Bagacay",
                "Bagong Silang I", "Bagong Silang II", "Bagong Silang III",
                "Bakiad", "Bautista", "Bayabas", "Bayan-bayan", "Benit",
                "Bulhao", "Cabatuhan", "Cabusay", "Calabasa", "Canapawan",
                "Daguit", "Dalas", "Dumagmang", "Exciban", "Fundado",
                "Guinacutan", "Guisican", "Gumamela", "Iberica", "Kalamunding",
                "Lugui", "Mabilo I", "Mabilo II", "Macogon", "Mahawan-hawan",
                "Malangcao-Basud", "Malasugui", "Malatap", "Malaya", "Malibago",
                "Maot", "Masalong", "Matanlang", "Napaod", "Pag-asa",
                "Pangpang", "Pinya", "San Antonio", "San Francisco",
                "Santa Cruz", "Submarkin", "Talobatib", "Tigbinan", "Tulay na Lupa"
        ));

        BARANGAYS.put("Mercedes", Arrays.asList(
                "Apuao", "Barangay I", "Barangay II", "Barangay III",
                "Barangay IV", "Barangay V", "Barangay VI", "Barangay VII",
                "Caringo", "Catandunganon", "Cayucyucan", "Colasi",
                "Del Rosario", "Gaboc", "Hamoraon", "Hinipaan", "Lalawigan",
                "Lanot", "Mambungalon", "Manguisoc", "Masalongsalong",
                "Matoogtoog", "Pambuhan", "Quinapaguian", "San Roque", "Tarum"
        ));

        BARANGAYS.put("Paracale", Arrays.asList(
                "Awitan", "Bagumbayan", "Bakal", "Batobalani", "Calaburnay",
                "Capacuan", "Casalugan", "Dagang", "Dalnac", "Dancalan",
                "Gumaus", "Labnig", "Macolabo Island", "Malacbang", "Malaguit",
                "Mampungo", "Mangkasay", "Maybato", "Palanas",
                "Pinagbirayan Malaki", "Pinagbirayan Munti", "Poblacion Norte",
                "Poblacion Sur", "Tabas", "Talusan", "Tawig", "Tugos"
        ));

        BARANGAYS.put("San Lorenzo Ruiz", Arrays.asList(
                "Daculang Bolo", "Dagotdotan", "Langga", "Laniton", "Maisog",
                "Mampurog", "Manlimonsito", "Matacong", "Salvacion",
                "San Antonio", "San Isidro", "San Ramon"
        ));

        BARANGAYS.put("San Vicente", Arrays.asList(
                "Asdum", "Cabanbanan", "Calabagas", "Fabrica", "Iraya Sur",
                "Man-ogob", "Poblacion District I", "Poblacion District II", "San Jose"
        ));

        BARANGAYS.put("Santa Elena", Arrays.asList(
                "Basiad", "Bulala", "Don Tomas", "Guitol", "Kabuluan",
                "Kagtalaba", "Maulawin", "Patag Ibaba", "Patag Iraya",
                "Plaridel", "Polungguitguit", "Rizal", "Salvacion",
                "San Lorenzo", "San Pedro", "San Vicente", "Santa Elena",
                "Tabugon", "Villa San Isidro"
        ));

        BARANGAYS.put("Talisay", Arrays.asList(
                "Binanuaan", "Caawigan", "Cahabaan", "Calintaan", "Del Carmen",
                "Gabon", "Itomang", "Poblacion", "San Francisco", "San Isidro",
                "San Jose", "San Nicolas", "Santa Cruz", "Santa Elena",
                "Santo Niño"
        ));

        BARANGAYS.put("Vinzons", Arrays.asList(
                "Aguit-it", "Banocboc", "Barangay I", "Barangay II",
                "Barangay III", "Cagbalogo", "Calangcawan Norte",
                "Calangcawan Sur", "Guinacutan", "Mangcawayan", "Mangcayo",
                "Manlucugan", "Matango", "Napilihan", "Pinagtigasan",
                "Sabang", "Santo Domingo", "Singi", "Sula"
        ));
    }

    /** Returns barangays for a given municipality, or empty list if not found. */
    public static List<String> getBarangays(String municipality) {
        List<String> list = BARANGAYS.get(municipality);
        return list != null ? list : java.util.Collections.emptyList();
    }
}
