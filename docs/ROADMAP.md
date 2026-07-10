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

## v0.2.1 — Previous release

Version bumped across all modules. Vulnerable Spring dependencies patched. Unused JS/theme code removed.

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

## v0.2.3 - Implement game-recorder module

- The idea behind this module is to record the game state using hooks on top of existing abstract methods so it can be reused for any game type there is to simplify persistence.
- After implementing the module the persistence from the server module should be mostly moved to the game-recorder module and the server module should only be responsible for routing the requests to the correct service.

---

# v0.2.3 → v0.3.0 — Second Game (Treseta)

- Reuse all the existing game logic to create Treseta logic in the `game-logic` -> `games` -> `treseta` module
- Create server-side logic for Treseta in the `server` module while creating all the necessary endpoints 
and sticking to the existing abstracted `GameController`.
- Create the Treseta persistence logic and game-stats logic in the `server` module while creating 
`GameHistoryService` and `GameStatsService` that will reroute every gametype to its own service.
- Create the Treseta UI in the `server` module using thymeleaf templates and existing game logic for briskula, they are
pretty similar in gameplay so there shouldn't be too much work if the game logic is separated from briskula.js correctly

---
