package noct.interpreter;

import noct.lexer.StringTemplate;
import noct.parser.ast.Ast;
import noct.parser.ast.Expr;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A running game: the tree-walking interpreter for Noct.
 *
 * <p>The game reacts to player commands ({@code go}, {@code inspect},
 * {@code use}, ...) by running the matching event handlers from the
 * program. Handlers are executed directly from the AST, one action at a time.
 *
 * <p>The program must have passed the {@link noct.semantic.Checker}; the
 * interpreter relies on that and does not re-check names or types.
 */
public final class Game {

    public static final String THE_END = "-- THE END --";

    private static final String HELP = """
            Commands:
              look                    describe the room
              inspect <target>        examine a door or object
              use <item> on <target>  use an item you carry
              go <door>               walk through a door
              inventory               list what you carry (also: inv)
              help                    show this list
              quit                    leave the game""";

    private final World world;
    private final GameState state = new GameState();
    private final Evaluator evaluator = new Evaluator(state);
    private final PrintStream out;
    private boolean over = false;

    /** Thrown by an {@code end} action to stop the game immediately, from any depth. */
    @SuppressWarnings("serial")
    private static final class GameOver extends RuntimeException {
        GameOver() {
            super(null, null, false, false);
        }
    }

    public Game(Ast.Program program, PrintStream out) {
        this.world = new World(program);
        this.out = out;
        initializeState(program);
    }

    /** Sets up vars, flags and door locks in declaration order. */
    private void initializeState(Ast.Program program) {
        for (Ast.Decl decl : program.declarations()) {
            switch (decl) {
                case Ast.FlagDecl d -> state.flags.put(d.name().lexeme(), d.initialValue());
                case Ast.VarDecl d -> state.vars.put(d.name().lexeme(), evaluator.number(d.initializer()));
                case Ast.RoomDecl d -> {
                    for (Ast.DoorDecl door : world.rooms.get(d.name().lexeme()).doors().values()) {
                        state.setLocked(d.name().lexeme(), door.name().lexeme(), door.locked());
                    }
                }
                case Ast.ItemDecl d -> { }
            }
        }
    }

    /** Puts the player in the starting room (the first room declared). */
    public void start() {
        guard(() -> enter(world.startRoom, false));
    }

    public boolean isOver() {
        return over;
    }

    public GameState state() {
        return state;
    }

    // ---------------------------------------------------------------------
    // Player commands
    // ---------------------------------------------------------------------

    /** Executes one line of player input. Blank lines are ignored. */
    public void execute(String input) {
        if (over) return;

        String[] words = input.trim().split("\\s+");
        if (words[0].isEmpty()) return;

        String verb = words[0].toLowerCase(Locale.ROOT);
        guard(() -> {
            switch (verb) {
                case "look" -> look();
                case "inspect" -> {
                    if (words.length == 2) inspect(words[1]);
                    else out.println("Inspect what? Usage: inspect <target>");
                }
                case "use" -> {
                    if (words.length == 4 && words[2].equalsIgnoreCase("on")) use(words[1], words[3]);
                    else out.println("Usage: use <item> on <target>");
                }
                case "go" -> {
                    if (words.length == 2) go(words[1]);
                    else out.println("Go where? Usage: go <door>");
                }
                case "inventory", "inv" -> inventory();
                case "help" -> out.println(HELP);
                case "quit" -> {
                    out.println("Goodbye.");
                    over = true;
                }
                default -> out.println("I don't understand '" + words[0] + "'. Type 'help' for a list of commands.");
            }
        });
    }

    private void look() {
        World.Room room = currentRoom();
        out.println(header(room));

        if (!room.objects().isEmpty()) {
            List<String> objects = new ArrayList<>();
            for (Ast.ObjectDecl o : room.objects().values()) {
                objects.add(World.text(o.displayName()) + " (" + o.name().lexeme() + ")");
            }
            out.println("You see: " + String.join(", ", objects));
        }

        if (room.doors().isEmpty()) {
            out.println("There is no way out.");
        } else {
            List<String> exits = new ArrayList<>();
            for (String door : room.doors().keySet()) {
                exits.add(state.isLocked(room.name(), door) ? door + " (locked)" : door);
            }
            out.println("Exits: " + String.join(", ", exits));
        }
    }

    private void inspect(String target) {
        World.Room room = currentRoom();
        Ast.OnInspect handler = room.onInspect().get(target);

        if (handler != null) {
            run(handler.body());
        } else if (room.doors().containsKey(target)) {
            out.println(target + (state.isLocked(room.name(), target) ? " is locked." : " is not locked."));
        } else if (room.objects().containsKey(target)) {
            out.println("Nothing unusual about the " + World.text(room.objects().get(target).displayName()) + ".");
        } else {
            out.println("There is no '" + target + "' here.");
        }
    }

    private void use(String item, String target) {
        World.Room room = currentRoom();

        if (!state.inventory.contains(item)) {
            out.println("You don't have '" + item + "'.");
            return;
        }
        if (!room.doors().containsKey(target) && !room.objects().containsKey(target)) {
            out.println("There is no '" + target + "' here.");
            return;
        }

        Ast.OnUse handler = room.useHandler(item, target);
        if (handler == null) {
            out.println("Nothing happens.");
        } else {
            run(handler.body());
        }
    }

    private void go(String doorName) {
        World.Room room = currentRoom();
        Ast.DoorDecl door = room.doors().get(doorName);

        if (door == null) {
            if (room.objects().containsKey(doorName)) {
                out.println("You can't go through the " + World.text(room.objects().get(doorName).displayName()) + ".");
            } else {
                out.println("There is no exit called '" + doorName + "' here.");
            }
            return;
        }

        if (state.isLocked(room.name(), doorName)) {
            String key = door.requiredItem() == null ? null : door.requiredItem().lexeme();
            if (key != null && state.inventory.contains(key)) {
                state.setLocked(room.name(), doorName, false);
                out.println("You unlock " + doorName + " with the " + world.itemNames.get(key) + ".");
            } else {
                out.println(doorName + " is locked.");
                return;
            }
        }

        enter(door.target().lexeme(), true);
    }

    private void inventory() {
        if (state.inventory.isEmpty()) {
            out.println("You are carrying nothing.");
            return;
        }
        List<String> items = new ArrayList<>();
        for (String item : state.inventory) items.add(world.itemNames.get(item) + " (" + item + ")");
        out.println("You are carrying: " + String.join(", ", items));
    }

    private void enter(String roomName, boolean blankLineBefore) {
        state.currentRoom = roomName;
        World.Room room = currentRoom();

        if (blankLineBefore) out.println();
        out.println(header(room));
        for (Ast.OnEnter handler : room.onEnter()) run(handler.body());
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    private void run(List<Ast.Action> actions) {
        for (Ast.Action action : actions) perform(action);
    }

    private void perform(Ast.Action action) {
        switch (action) {
            case Ast.Say a -> out.println(render(a.template()));
            case Ast.Give a -> state.inventory.add(a.item().lexeme());
            case Ast.Take a -> state.inventory.remove(a.item().lexeme());
            case Ast.Lock a -> state.setLocked(state.currentRoom, a.door().lexeme(), true);
            case Ast.Unlock a -> state.setLocked(state.currentRoom, a.door().lexeme(), false);
            case Ast.Set a -> {
                String name = a.target().lexeme();
                if (state.vars.containsKey(name)) state.vars.put(name, evaluator.number(a.value()));
                else state.flags.put(name, evaluator.bool(a.value()));
            }
            case Ast.If a -> run(evaluator.bool(a.condition()) ? a.thenBranch() : a.elseBranch());
            case Ast.End a -> {
                out.println(render(a.template()));
                out.println();
                out.println(THE_END);
                throw new GameOver();
            }
        }
    }

    /** Fills in {name} references with the current values of vars and flags. */
    private String render(StringTemplate template) {
        StringBuilder sb = new StringBuilder();
        for (StringTemplate.Part part : template.parts()) {
            switch (part) {
                case StringTemplate.Text t -> sb.append(t.text());
                case StringTemplate.Ref r -> sb.append(evaluator.evaluate(new Expr.Name(r.name())));
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private World.Room currentRoom() {
        return world.rooms.get(state.currentRoom);
    }

    private static String header(World.Room room) {
        return "== " + room.displayName() + " ==";
    }

    /** Runs a step of the game, turning an {@code end} action into the game-over state. */
    private void guard(Runnable step) {
        try {
            step.run();
        } catch (GameOver end) {
            over = true;
        }
    }
}
