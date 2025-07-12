import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SeriesStorage {
    private static final String FILE_NAME = "series_data.json";
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<Long, Map<String, int[]>> load() {
        try {
            File file = new File(FILE_NAME);
            if (file.exists()) {
                var typeFactory = mapper.getTypeFactory();
                var outerMapType = typeFactory.constructParametricType(
                        Map.class,
                        typeFactory.constructType(Long.class),
                        typeFactory.constructMapType(Map.class, String.class, int[].class)
                );
                return mapper.readValue(file, outerMapType);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new HashMap<>();
    }

    public void save(Map<Long, Map<String, int[]>> data) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(FILE_NAME), data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}