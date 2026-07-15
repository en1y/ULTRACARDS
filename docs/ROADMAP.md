# ULTRACARDS Roadmap

# v0.1 — Foundation ✅

**Project structure**
- Multi-module Maven setup: `cards`, `game-logic`, `server`, `ui`, `game-gateway`, `game-recorder`, `console-ui`
- Template modules for cards and games (`base-card-game`)

**Card implementations**
- `italian-cards` — full suit/value implementation with i18n resource bundles
- `poker-cards` — full suit/value implementation with i18n resource bundles
- `CardValueInterface` returns `String` (not `char`)

**Game abstractions**
- `DeckInterface`, `HandInterface`, `GameInterface`, `AbstractDeck`, `AbstractHand`, `AbstractGame`
- Observer pattern classes for game events

---

# v0.1.0 → v0.2.0 — First Game (Briskula) ✅

### Game logic
- Briskula game rules, turn logic, card handling
- 2v2 mode with card-showing mechanic
- `GameManager` and `GameService` for Briskula
- Separated Briskula logic from general game-logic module

### Backend (server module)
- User auth with session/token management, token rotation, reuse window for old tokens
- Lobby system: create, join, invite, in-memory management
- WebSocket endpoints for real-time game state
- Persistence layer with Flyway migrations

### Social features
- Friends system (add, remove, invite to lobby)
- User presence/online status
- Private friend chat with read states
- Notification system (CRUD + WebSocket push)
- User search by username / ID with pagination
- OWASP HTML sanitizer for user input

### Game history
- Per-game history tracking with replay UI
- Detailed game history: moves, scores, round-by-round view
- Game stats tracking per user

### UI (web-ui module)
- Desktop game table with card flip animations, flying-card animations, previous-round panel
- Card image caching for performance
- Mobile-first redesign: full mobile UI, lobby/chat tweaks
- User profile page with history display, friend management, search bar in header

### Infrastructure
- `game-gateway` module: async WebSocket handling, gateway client, auth/profile/card/chat services
- `start-server.sh` / `start-server.bat` scripts

---

## v0.2.1 - Implement multilingual support ✅

- Add i18n support for UI strings (English, Croatian, Ukrainian, German)
- Update the UI to support and select the best language by default based on browser settings
- Add a language selector in the UI for users to change the language manually

---

## v0.2.2 - Reconstruct profile pill in the header ✅

- Make the profile pill more usable and visually appealing
- All the options should be more hidden and accessible through a better implemented dropdown menu

---

## v0.2.3 - Implement game-recorder module ✅

- Add a non-invasive `GameRecordingHook` to the shared game template. Games notify it when they start, a round starts or ends, a card is played, and the game ends, without depending on recorder or server code.
- Implement the `game-recorder` module, which turns those events into a replayable record: game metadata and timestamps, ordered players, starting hands, card plays, round winners, and game- or round-specific attributes.
- Provide a Briskula recorder specialization that also captures the selected game configuration, teams, trump card, and points earned in each round.
- Keep recorder lifecycle outside live game entities: the server attaches a recorder when it creates a game and persists the completed aggregate when that game finishes.
- Move Briskula history to recorder-owned tables and migrate existing games, players, teams, rounds, cards, hands, winners, and scores so players keep their previous game history.

---

# v0.2.3 → v0.3.0 — Second Game (Treseta) ✅

- Reuse all the existing game logic to create Treseta logic in the `game-logic` -> `games` -> `treseta` module
- Create server-side logic for Treseta in the `server` module while creating all the necessary endpoints 
and sticking to the existing abstracted `GameController`.
- Create the Treseta persistence logic and game-stats logic in the `server` module while creating 
`GameHistoryService` and `GameStatsService` that will reroute every gametype to its own service.
- Create the Treseta UI in the `server` module using thymeleaf templates and existing game logic for briskula, they are
pretty similar in gameplay so there shouldn't be too much work if the game logic is separated from briskula.js correctly

---
