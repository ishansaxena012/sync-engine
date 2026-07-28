import java.util.UUID;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
class BaseEntity {
    protected UUID id;
}

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
class CanvasObject extends BaseEntity {
    private String type;
}

public class TestBuilder {
    public static void main(String[] args) {
        UUID id = UUID.randomUUID();
        CanvasObject obj = CanvasObject.builder().id(id).type("RECT").build();
        System.out.println("ID: " + obj.getId());
    }
}
