package com.mac.bry.validationsystem.measurement;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Serwis do zarządzania seriami pomiarowymi
 */
public interface MeasurementSeriesService {

    /**
     * Przesyła i parsuje wiele plików .vi2 z przypisaną pozycją rejestratora i
     * urządzeniem.
     * Zwraca wynik operacji wraz z ostrzeżeniami i błędami.
     */
    UploadResult uploadVi2Files(MultipartFile[] files, RecorderPosition position,
                                Long deviceId, boolean isReferenceRecorder) throws IOException;

    /**
     * Pobiera wszystkie serie pomiarowe
     */
    List<MeasurementSeriesDto> getAllSeries();

    /**
     * Pobiera tylko NIEużyte serie pomiarowe (dostępne do walidacji)
     */
    List<MeasurementSeriesDto> getUnusedSeries();

    /**
     * Dzień 10: Pobiera dostępne dla użytkownika NIEużyte serie
     */
    List<MeasurementSeriesDto> getAccessibleUnusedSeries();

    /**
     * Pobiera tylko użyte serie pomiarowe
     */
    List<MeasurementSeriesDto> getUsedSeries();

    /**
     * Dzień 10: Pobiera dostępne dla użytkownika UŻYTE serie
     */
    List<MeasurementSeriesDto> getAccessibleUsedSeries();

    /**
     * Pobiera serię według ID
     */
    MeasurementSeriesDto getSeriesById(Long id);

    /**
     * Pobiera szczegółowe dane serii (wraz z punktami pomiarowymi)
     */
    MeasurementSeries getSeriesWithPoints(Long id);

    /**
     * Usuwa serię pomiarową
     */
    void deleteSeries(Long id);

    /**
     * Pobiera serie według numeru seryjnego rejestratora
     */
    List<MeasurementSeriesDto> getSeriesByRecorderSerial(String serialNumber);

    /**
     * Oblicza statystyki serii pomiarowej (Tmin, Tmax, Tavg, StdDev, MKT).
     * Pobiera energię aktywacji z powiązanego urządzenia/materiału.
     */
    void calculateStatistics(MeasurementSeries series);

    /**
     * Pobiera wszystkie NIEużyte serie dla danego urządzenia
     */
    List<MeasurementSeriesDto> getUnusedSeriesByDevice(Long deviceId);

    /**
     * Zwraca liczbę serii pomiarowych dostępnych dla zalogowanego użytkownika.
     * Filtrowanie jest identyczne jak w getAccessibleSeries — wg firmy/działu/laboratorium.
     */
    long countAccessibleSeries();

    /**
     * Zwraca sumę punktów pomiarowych (measurementCount) ze wszystkich dostępnych serii.
     * Punkty pomiarowe to pojedyncze odczyty temperatury z pliku .vi2.
     */
    long countAccessibleMeasurementPoints();
}
