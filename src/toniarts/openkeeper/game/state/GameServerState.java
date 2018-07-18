/*
 * Copyright (C) 2014-2017 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenKeeper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenKeeper.  If not, see <http://www.gnu.org/licenses/>.
 */
package toniarts.openkeeper.game.state;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.math.Vector2f;
import com.simsilica.es.EntityId;
import java.awt.Point;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import toniarts.openkeeper.Main;
import toniarts.openkeeper.game.controller.GameController;
import toniarts.openkeeper.game.controller.IGameWorldController;
import toniarts.openkeeper.game.controller.IMapController;
import toniarts.openkeeper.game.controller.IPlayerController;
import toniarts.openkeeper.game.data.Keeper;
import toniarts.openkeeper.game.listener.MapListener;
import toniarts.openkeeper.game.listener.PlayerActionListener;
import toniarts.openkeeper.game.map.MapTile;
import toniarts.openkeeper.game.state.session.GameSessionServerService;
import toniarts.openkeeper.game.state.session.GameSessionServiceListener;
import toniarts.openkeeper.tools.convert.map.KwdFile;

/**
 * The game state that actually runs the game. Has no relation to visuals.
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class GameServerState extends AbstractAppState {

    private Main app;

    private Thread loader;
    private AppStateManager stateManager;

    private final String level;
    private final KwdFile kwdFile;
    private final toniarts.openkeeper.game.data.Level levelObject;

    private boolean campaign;
    private final GameSessionServerService gameService;
    private IMapController mapController;
    private final MapListener mapListener = new MapListenerImpl();
    private final GameSessionServiceListener gameSessionListener = new GameSessionServiceListenerImpl();
    private final PlayerActionListener playerActionListener = new PlayerActionListenerImpl();
    private GameController gameController;
    private IGameWorldController gameWorldController;

    private static final Logger LOGGER = Logger.getLogger(GameServerState.class.getName());

    /**
     * Single use game states
     *
     * @param level the level to load
     * @param players players participating in this game
     * @param campaign whether this is a campaign level or not
     * @param gameService the game service
     */
    public GameServerState(KwdFile level, List<Keeper> players, boolean campaign, GameSessionServerService gameService) {
        this.level = null;
        this.kwdFile = level;
        this.levelObject = null;
        this.campaign = campaign;
        this.gameService = gameService;

        // Add the listener
        gameService.addGameSessionServiceListener(gameSessionListener);

        // Start loading game
        loadGame(players);
    }

    private void loadGame(List<Keeper> players) {
        loader = new GameLoader(players);
        loader.start();
    }

    @Override
    public void initialize(final AppStateManager stateManager, final Application app) {
        this.app = (Main) app;
        this.stateManager = stateManager;
    }

    /**
     * If you are getting rid of the game state, use this so that all the
     * related states are detached on the same render loop. Otherwise the app
     * might crash.
     */
    public void detach() {
        if (loader != null && loader.isAlive()) {
            loader.interrupt();
        }
        stateManager.detach(this);

        if (gameController != null) {
            try {
                gameController.close();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to close the game!", ex);
            }
        }
    }

    @Override
    public void cleanup() {

        // Detach
        detach();

        super.cleanup();
    }

    /**
     * Load the game
     */
    private class GameLoader extends Thread {

        private final List<Keeper> players;

        public GameLoader(List<Keeper> players) {
            super("GameLoader");

            this.players = players;
        }

        @Override
        public void run() {

            // Make sure the KWD file is fully loaded
            kwdFile.load();

            // Create the central game controller
            gameController = new GameController(kwdFile, players, gameService.getEntityData(), kwdFile.getVariables(), gameService);
            gameController.createNewGame();

            gameWorldController = gameController.getGameWorldController();
            mapController = gameWorldController.getMapController();
            gameWorldController.addListener(playerActionListener);

            // Send the the initial game data
            gameService.sendGameData(gameController.getPlayers(), mapController.getMapData());

            // Set up a listener for the map
            mapController.addListener(mapListener);

            // Set up a listener for the player changes, they are per player
            for (IPlayerController playerController : gameController.getPlayerControllers()) {
                playerController.addListener(gameService);
            }

            // Start the actual game
            gameController.startGame();

            // Nullify the thread object
            loader = null;
        }
    }

    /**
     * Listen for basically clients' requests
     */
    private class GameSessionServiceListenerImpl implements GameSessionServiceListener {

        public GameSessionServiceListenerImpl() {
        }

        @Override
        public void onSelectTiles(Vector2f start, Vector2f end, boolean select, short playerId) {
            mapController.selectTiles(start, end, select, playerId);
        }

        @Override
        public void onBuild(Vector2f start, Vector2f end, short roomId, short playerId) {
            gameWorldController.build(start, end, playerId, roomId);
        }

        @Override
        public void onSell(Vector2f start, Vector2f end, short playerId) {
            gameWorldController.sell(start, end, playerId);
        }

        @Override
        public void onInteract(EntityId entity, short playerId) {
            gameWorldController.interact(entity, playerId);
        }

        @Override
        public void onPickUp(EntityId entity, short playerId) {
            gameWorldController.pickUp(entity, playerId);
        }

        @Override
        public void onDrop(EntityId entity, Point tile, Vector2f coordinates, EntityId dropOnEntity, short playerId) {
            gameWorldController.drop(entity, tile, coordinates, dropOnEntity, playerId);
        }

        @Override
        public void onTransitionEnd(short playerId) {
            // We are not really interested in this, the status is also tracked in the local clients
        }

        @Override
        public void onPauseRequest(short playerId) {
            // TODO: We should only allow the server owner etc. to pause, otherwise, send a system message that player x wants to pause?
            gameController.pauseGame();

        }

        @Override
        public void onResumeRequest(short playerId) {
            // TODO: We should only allow the server owner etc. to pause, otherwise, send a system message that player x wants to pause?
            gameController.resumeGame();
        }

        @Override
        public void onExitGame(short playerId) {
            // TODO: Close the server and game only when everybody has left
            stateManager.detach(GameServerState.this);
        }
    }

    /**
     * Listen for the map changes
     */
    private class MapListenerImpl implements MapListener {

        @Override
        public void onTilesChange(List<MapTile> updatedTiles) {
            gameService.updateTiles(updatedTiles);
        }

        @Override
        public void onTileFlash(List<Point> points, boolean enabled, short keeperId) {
            gameService.flashTiles(points, enabled, keeperId);
        }
    }

    /**
     * Listen for the player actions
     */
    private class PlayerActionListenerImpl implements PlayerActionListener {

        @Override
        public void onBuild(short keeperId, List<MapTile> tiles) {
            gameService.onBuild(keeperId, tiles);
        }

        @Override
        public void onSold(short keeperId, List<MapTile> tiles) {
            gameService.onSold(keeperId, tiles);
        }

    }

}
