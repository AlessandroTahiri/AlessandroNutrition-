# Alessandro Nutrition v2.4.1 completa

Pacchetto di sostituzione per il progetto Android.

## Implementato
- Home aggiornata: Idratazione, Alimentazione, Integratori, Attività con +, Vita personale con +, Spesa, Resoconto.
- Obiettivi e Trofei rimossi dalla Home e disponibili solo dal menu.
- Integratori con dose, giorni, ora, note, promemoria e spunta assunzione.
- Piano settimanale lunedì-domenica con settimana corrente, navigazione settimane, domenica libera, cena facoltativa.
- Calendario con giorno di oggi evidenziato, giorno selezionato distinto e data completa.
- Lista spesa: prodotto, quantità, unità, categoria, nota, acquistato, sezioni Da comprare/Acquistati, contatore, segna tutti, elimina acquistati.
- Generazione lista dalla settimana corrente con anteprima e aggregazione semplice per stesso alimento/unità.
- Obiettivi automatici/manuali e categorie libere.
- Catalogo Trofei progressivo.
- Idratazione con +250/+500 e progressione.
- Resoconto, Sonno, Peso/misure, Vita personale.
- Professionisti con invito, permessi, revoca, proposte tramite nota e senza modifica diretta.
- Impostazioni tema Chiaro/Scuro/Automatico + colore principale.
- Widget Android reale grande, ridimensionabile, con acqua e dashboard giornaliera.
- Portale web professionisti come base UI.
- Versione 2.4 / versionCode 7.

## Limite importante della parte Professionisti
Il collegamento remoto reale non può essere attivato in sicurezza senza un progetto backend configurato (ad es. Firebase/Supabase) e relative credenziali. Il pacchetto prepara interfaccia e modello di sicurezza, ma NON finge che i dati cloud siano già sincronizzati.

## Installazione nel repository
Copia le cartelle del pacchetto sopra il progetto esistente mantenendo i percorsi. Non eliminare `app/src/main/assets/annual_plan.json` né le icone già presenti.

## Correzione v2.4.1
- Corretto l'errore di sintassi Kotlin in `ScreenTitle` che bloccava `compileDebugKotlin` su GitHub Actions.
- Versione portata a 2.4.1 / versionCode 8.
