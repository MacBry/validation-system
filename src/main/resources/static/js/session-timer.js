/**
 * System Walidacji - Proaktywne Monitorowanie Sesji
 * 
 * Skrypt odpowiada za:
 * 1. Odliczanie czasu do wygaśnięcia sesji.
 * 2. Wyświetlenie ostrzeżenia (modal) przed końcem sesji.
 * 3. Obsługę przedłużenia sesji (ping do API).
 * 4. Automatyczne przekierowanie do logowania po wygaśnięciu.
 */

document.addEventListener('DOMContentLoaded', function() {
    // Pobierz konfigurację z meta-tagów
    const timeoutSeconds = parseInt(document.querySelector('meta[name="session-timeout"]').getAttribute('content')) || 1800; // default 30 min
    const warningThreshold = 120; // 2 minuty przed końcem
    
    let timeRemaining = timeoutSeconds;
    let timerInterval;
    let countdownInterval;
    
    const warningModal = new bootstrap.Modal(document.getElementById('sessionExpirationModal'));
    const extendBtn = document.getElementById('extendSessionBtn');
    const timerDisplay = document.getElementById('sessionTimerDisplay');
    
    // Pobierz dane CSRF
    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    function startTimer() {
        clearInterval(timerInterval);
        timeRemaining = timeoutSeconds;
        
        timerInterval = setInterval(() => {
            timeRemaining--;
            
            // Pokaż ostrzeżenie
            if (timeRemaining === warningThreshold) {
                showWarning();
            }
            
            // Przekieruj po wygaśnięciu
            if (timeRemaining <= 0) {
                clearInterval(timerInterval);
                window.location.href = '/login?timeout';
            }
        }, 1000);
    }

    function showWarning() {
        warningModal.show();
        startCountdown();
    }

    function startCountdown() {
        clearInterval(countdownInterval);
        let countdown = warningThreshold;
        
        countdownInterval = setInterval(() => {
            countdown--;
            const mins = Math.floor(countdown / 60);
            const secs = countdown % 60;
            timerDisplay.textContent = `${mins}:${secs < 10 ? '0' : ''}${secs}`;
            
            if (countdown <= 0) {
                clearInterval(countdownInterval);
            }
        }, 1000);
    }

    function extendSession() {
        fetch('/api/session/ping', {
            method: 'POST',
            headers: {
                [csrfHeader]: csrfToken,
                'Content-Type': 'application/json'
            }
        })
        .then(response => {
            if (response.ok) {
                warningModal.hide();
                clearInterval(countdownInterval);
                startTimer();
                console.log('Session extended successfully');
            } else {
                console.error('Failed to extend session');
            }
        })
        .catch(error => console.error('Error extending session:', error));
    }

    // Event listener dla przycisku przedłużenia
    if (extendBtn) {
        extendBtn.addEventListener('click', extendSession);
    }

    // Resetuj timer przy interakcji użytkownika (opcjonalne, ale zalecane)
    // UWAGA: Lokalne przesunięcie myszką nie przedłuża sesji na SERWERZE.
    // Dlatego ten licznik jest pesymistyczny - zakłada, że tylko aktywność sieciowa przedłuża sesję.
    
    // Zainicjuj timer
    startTimer();
});
