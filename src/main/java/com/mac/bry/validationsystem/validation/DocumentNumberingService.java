package com.mac.bry.validationsystem.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generuje kolejne numery dokumentów w formacie PREFIX/LAB/ROK/NNN.
 * Przykład: SW/LZTHLA/2026/001
 * Blokada pesymistyczna na sekwencerze gwarantuje unikalność nawet przy równoległych żądaniach.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentNumberingService {

    private final DocumentSequenceRepository sequenceRepository;

    @Transactional
    public String generateNextNumber(String typePrefix, String labAbbrev, int year) {
        DocumentSequence seq = sequenceRepository
                .findByTypePrefixAndLabAbbrevAndYear(typePrefix, labAbbrev, year)
                .orElseGet(() -> {
                    DocumentSequence newSeq = new DocumentSequence();
                    newSeq.setTypePrefix(typePrefix);
                    newSeq.setLabAbbrev(labAbbrev);
                    newSeq.setYear(year);
                    newSeq.setLastNumber(0);
                    return newSeq;
                });

        seq.setLastNumber(seq.getLastNumber() + 1);
        sequenceRepository.save(seq);

        String number = String.format("%s/%s/%d/%04d", typePrefix, labAbbrev, year, seq.getLastNumber());
        log.info("Wygenerowano numer dokumentu: {}", number);
        return number;
    }
}
