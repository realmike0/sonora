# Privacy policy di Sonora

Ultimo aggiornamento: v0.51-beta.

## Principio
Sonora funziona senza account e senza server. Il tracciamento è **facoltativo, spento di default e parte solo se lo accetti e verifichi la tua email**.

## Cosa resta sul telefono (sempre)
Mi piace, playlist, cache audio e un registro degli ascolti (brano, artista, orario, durata) usato per Wrapped locale e widget. Non lascia il telefono se non attivi il tracciamento.

## Se accetti il tracciamento
Inviamo al nostro server solo gli ascolti **dal momento in cui verifichi l'email**: id del brano, titolo, artista, copertina, orario e durata ascoltata. Servono unicamente a creare il tuo Wrapped.

## Telegram o email (verifica)
Per provare che sei tu puoi scegliere Telegram o l'email. Con Telegram condividi il tuo numero di telefono con il nostro bot: Telegram garantisce che sia il tuo e noi lo conserviamo solo come impronta (hash SHA-256), non in chiaro. Con l'email ti mandiamo un codice a 6 cifre; anche l'email è conservata solo come impronta. Il provider di invio (Brevo) vede l'indirizzo per recapitare il messaggio.

## Sonora ID (facoltativo)

Se crei un Sonora ID (email e password) per sincronizzare i brani preferiti tra dispositivi:

- **Email:** viene salvata solo come impronta (hash), mai in chiaro. Per questo non possiamo scriverti né recuperare la password: se la dimentichi, il Sonora ID non è recuperabile.
- **Email:** salvata cifrata (AES-GCM, chiave solo sul server) per verificarla con un codice a 6 cifre e per il recupero dell'account; per riconoscerla usiamo anche un'impronta (HMAC). Non è mai visibile in chiaro nei log né agli amministratori.
- **Password:** viene salvata solo in forma protetta (PBKDF2-SHA256 con sale casuale), mai in chiaro.
- **Preferiti:** per ogni brano piaciuto salviamo identificativo, titolo, artista, copertina e durata, così si sincronizzano tra i tuoi dispositivi.
- **Sessioni:** l'accesso dura 90 giorni, poi va rifatto.
- **Accessi:** i tentativi di accesso sono limitati per proteggere l'account; usiamo l'indirizzo IP solo in forma di impronta per questo limite.
- **Cancellazione:** da Impostazioni → Profilo → Elimina Sonora ID cancelli subito dal server account, sessioni e preferiti. Sul telefono resta tutto.
- Il Sonora ID è separato dal tracciamento del Wrapped: puoi usare l'uno senza l'altro.

## Cosa non facciamo
Niente pubblicità, niente vendita o cessione dei dati, niente profilazione, nessun altro uso degli ascolti.

## Rifiuto e revoca
Se rifiuti, non tracciamo nulla. Puoi attivare il tracciamento più tardi dalle Impostazioni (con codice email), spegnerlo quando vuoi, ed **eliminare tutti i tuoi dati dal server** dal pulsante "Elimina i miei dati".

## Conservazione e sicurezza
I dati restano finché non li elimini. Comunicazione in HTTPS; codici validi 10 minuti, 5 tentativi massimi.

## Contatti
Apri una issue sul repository GitHub di Sonora.

## Dediche
Se pubblichi una dedica, il brano, il destinatario, il messaggio e il nome che scegli sono visibili a tutti gli utenti con un Sonora ID. Puoi eliminarla in qualsiasi momento; le dediche segnalate da 3 utenti vengono nascoste e quelle di un account eliminato vengono cancellate.

**Anti-spam:** quando scrivi una dedica conserviamo per 30 giorni l'impronta (HMAC, non l'indirizzo) del tuo IP. Serve solo a cancellare dediche di spam e a bloccare chi le invia; poi viene eliminata. Per scrivere dediche l'email deve essere verificata.

L'identificativo del Wrapped (email o numero di telefono) è un'impronta con chiave segreta del server: non si può ricavare l'email o il numero da quello che è salvato.
