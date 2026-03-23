# Claude Code Custom Agents
 
 W katalogu `.claude/agents/` znajdują się wyspecjalizowane "persony" (prompty systemowe) zaprojektowane pod kątem architektury oraz wymogów prawnych (GMP/GDP) tego konkretnego projektu w Spring Boot.
 
 Aby uruchomić Claude Code z wprowadzonym w określoną rolę asystentem, użyj flagi `-p` z odpowiednią ścieżką do agenta. Na Windows uruchom w konsoli (będąc w głównym katalogu projektu):
 
 ```bash
 # Architekt Spring Boot (optymalizacje bazy danych i plików binarnych)
 claude -p .claude/agents/spring-architect.md
 
 # Audytor GDP/GMP (obliczenia statystyczne, poprawność matematyczna, jakość logów)
 claude -p .claude/agents/gmp-analyst.md
 
 # Programista Frontendu & iText PDF (raportowanie do druku i wykresy)
 claude -p .claude/agents/ui-expert.md

 # Specjalista ds. Security i Compliance (podpisy elektroniczne, Annex 11, bezpieczeństwo Spring)
 claude -p .claude/agents/security-specialist.md

 # Inżynier Niskopoziomowy (dekodowanie binarne, format OLE2, optymalizacja I/O)
 claude -p .claude/agents/low-level-engineer.md
 ```
 
 Instrukcje te powodują, że model zachowa się w 100% zgodnie z narzuconymi z góry regułami dotyczącymi wydajności (`spring-architect.md`), ścisłej poprawności wyników pod organy kontrolne (`gmp-analyst.md`), stylistyki (`ui-expert.md`), bezpieczeństwa (`security-specialist.md`) oraz głębokiej analizy danych binarnych (`low-level-engineer.md`).
