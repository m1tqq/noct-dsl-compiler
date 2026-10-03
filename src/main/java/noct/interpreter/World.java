package noct.interpreter;

import noct.lexer.StringTemplate;
import noct.lexer.Token;
import noct.parser.ast.Ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The static, unchanging part of a game, indexed from the AST for fast
 * lookup: rooms, their doors, objects and handlers, and item names.
 * Everything that changes during play lives in {@link GameState}.
 */
final class World {

    record Room(
            String name,
            String displayName,
            Map<String, Ast.DoorDecl> doors,
            Map<String, Ast.ObjectDecl> objects,
            List<Ast.OnEnter> onEnter,
            Map<String, Ast.OnInspect> onInspect,
            Map<String, Ast.OnUse> onUse) {

        Ast.OnUse useHandler(String item, String target) {
            return onUse.get(item + " " + target);
        }
    }

    final Map<String, Room> rooms = new LinkedHashMap<>();
    final Map<String, String> itemNames = new LinkedHashMap<>();
    final String startRoom;

    World(Ast.Program program) {
        String first = null;
        for (Ast.Decl decl : program.declarations()) {
            switch (decl) {
                case Ast.ItemDecl d -> itemNames.put(d.name().lexeme(), text(d.displayName()));
                case Ast.RoomDecl d -> {
                    rooms.put(d.name().lexeme(), index(d));
                    if (first == null) first = d.name().lexeme();
                }
                case Ast.FlagDecl d -> { }
                case Ast.VarDecl d -> { }
            }
        }
        this.startRoom = first;
    }

    private static Room index(Ast.RoomDecl decl) {
        Room room = new Room(decl.name().lexeme(), text(decl.displayName()),
                new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>(),
                new LinkedHashMap<>(), new LinkedHashMap<>());

        for (Ast.Member member : decl.members()) {
            switch (member) {
                case Ast.DoorDecl d -> room.doors.put(d.name().lexeme(), d);
                case Ast.ObjectDecl o -> room.objects.put(o.name().lexeme(), o);
                case Ast.OnEnter h -> room.onEnter.add(h);
                case Ast.OnInspect h -> room.onInspect.put(h.target().lexeme(), h);
                case Ast.OnUse h -> room.onUse.put(h.item().lexeme() + " " + h.target().lexeme(), h);
            }
        }
        return room;
    }

    static String text(Token string) {
        return ((StringTemplate) string.value()).source();
    }
}
