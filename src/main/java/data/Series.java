package data;

public class Series {
    private String name;
    private int season;
    private int episode;

    public Series(String name, int season, int episode) {
        this.name = name;
        this.season = season;
        this.episode = episode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSeason() {
        return season;
    }

    public void setSeason(int season) {
        this.season = season;
    }

    public int getEpisode() {
        return episode;
    }

    public void setEpisode(int episode) {
        this.episode = episode;
    }
}
