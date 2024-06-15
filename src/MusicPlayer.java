import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;

import java.io.*;
import java.util.ArrayList;

public class MusicPlayer extends PlaybackListener {
    // this will update isPaused more synchronously
    private static final Object playSignal = new Object();
    // reference to the GUI
    private MusicPlayerGUI musicPlayerGUI;
    // reference to the current song
    private Song currentSong;

    public Song getCurrentSong() {
        return currentSong;
    }

    private ArrayList<Song> playList;

    private int currentPlaylistIndex;
    // use JLayer library to create an AdvancedPlayer obj which will handle the audio playback
    private AdvancedPlayer advancedPlayer;
    // indicate if the song is paused
    private boolean isPaused;
    // indicate if the song is finished
    private boolean isFinished;

    private boolean pressNext, pressPrevious;
    // indicate where the music is stopped in milliseconds
    private int currentFrame;

    public void setCurrentFrame(int frame) {
        this.currentFrame = frame;
    }
    // track the current time of the song
    private int currentTimeInMilli;

    public void setCurrentTimeInMilli(int timeInMilli) {
        this.currentTimeInMilli = timeInMilli;
    }

    public MusicPlayer(MusicPlayerGUI musicPlayerGUI) {
        this.musicPlayerGUI = musicPlayerGUI;
    }

    public void loadSong(Song song) {
        currentSong = song;
        playList = null;

        // stop the song if possible
        if (!isFinished) {
            stopSong();
        }
        if (currentSong != null) {
            // reset frame
            currentFrame = 0;
            //reset current time in milliseconds
            currentTimeInMilli = 0;
            // update the GUI
            musicPlayerGUI.setPlaybackSliderValue(0);

            playCurrentSong();
        }
    }

    public void loadPlayList(File playListFile) {
        playList = new ArrayList<>();
        // store the paths from the text file into the playList array list
        try {
            FileReader fileReader = new FileReader(playListFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            // reach each line in the text file and store it in the playList array list
            String songPath;
            while ((songPath = bufferedReader.readLine()) != null) {
                Song song = new Song(songPath);
                playList.add(song);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (playList.size() > 0) {
            // reset playback slider
            musicPlayerGUI.setPlaybackSliderValue(0);
            currentTimeInMilli = 0;
            // update the current song to the first song in the playList
            currentSong = playList.get(0);
            // start from the beginning
            currentFrame = 0;
            // update the GUI
            musicPlayerGUI.enablePauseButtonDisablePlayButton();
            musicPlayerGUI.updateSongTitleAndArtist(currentSong);
            musicPlayerGUI.updatePlaybackSlider(currentSong);
            // start song
            playCurrentSong();
        }
    }

    public void pauseSong() {
        if (advancedPlayer != null) {
            isPaused = true;
            // stop the music
            stopSong();
        }
    }

    public void stopSong() {
        if (advancedPlayer != null) {
            advancedPlayer.stop();
            advancedPlayer.close();
            advancedPlayer = null;
        }
    }

    public void nextSong() {
        // no need to go to the next song if there is no playlist
        if (playList == null) {
            return;
        }
        // check if we are going out of range of the playlist
        if (currentPlaylistIndex + 1 > playList.size() - 1) {
            return;
        }

        pressNext = true;

        // stop the song if possible
        if (!isFinished) {
            stopSong();
        }
        //increase currentPlaylistIndex
        currentPlaylistIndex++;
        // update the current song
        currentSong = playList.get(currentPlaylistIndex);
        // reset frame
        currentFrame = 0;
        // reset current time in milliseconds
        currentTimeInMilli = 0;
        // update the GUI
        musicPlayerGUI.enablePauseButtonDisablePlayButton();
        musicPlayerGUI.updateSongTitleAndArtist(currentSong);
        musicPlayerGUI.updatePlaybackSlider(currentSong);
        // play the song
        playCurrentSong();
    }

    public void previousSong() {
        // no need to go to the next song if there is no playlist
        if (playList == null) {
            return;
        }

        pressPrevious = true;
        // check if we are going out of range of the playlist
        if (currentPlaylistIndex - 1 < 0) {
            return;
        }
        // stop the song if possible
        if (!isFinished) {
            stopSong();
        }
        //decrease currentPlaylistIndex
        currentPlaylistIndex--;
        // update the current song
        currentSong = playList.get(currentPlaylistIndex);
        // reset frame
        currentFrame = 0;
        // reset current time in milliseconds
        currentTimeInMilli = 0;
        // update the GUI
        musicPlayerGUI.enablePauseButtonDisablePlayButton();
        musicPlayerGUI.updateSongTitleAndArtist(currentSong);
        musicPlayerGUI.updatePlaybackSlider(currentSong);
        // play the song
        playCurrentSong();
    }

    public void playCurrentSong() {
        if (currentSong == null) {
            return;
        }
        try {
            // read the audio file from the file path
            FileInputStream fileInputStream = new FileInputStream(currentSong.getFilePath());
            BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
            // create an AdvancedPlayer obj to play the audio file
            advancedPlayer = new AdvancedPlayer(bufferedInputStream);
            advancedPlayer.setPlayBackListener(this);
            // start music
            startMusicThread();
            // start the playback slider thread
            startPlaybackSliderThread();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // create a thread to play the music
    private void startMusicThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isPaused) {
                        synchronized (playSignal) {
                            // update isPaused
                            isPaused = false;
                            // notify the other thread to continue
                            playSignal.notify();
                        }
                        // resume the music from where it was paused
                        advancedPlayer.play(currentFrame, Integer.MAX_VALUE);
                    } else {
                        // play the music from the beginning
                        advancedPlayer.play();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // create a thread that will handle updating the slider
    private void startPlaybackSliderThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isPaused) {
                    try {
                        //  wait till get notified by other thread
                        synchronized (playSignal) {
                            playSignal.wait();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                while (!isPaused && !isFinished && !pressNext && !pressPrevious) {
                    try {
                        // increment the current time
                        currentTimeInMilli++;
                        // calculate into frame value
                        int calculatedFrame = (int) ((double) currentTimeInMilli * 2.08 * currentSong.getFrameRatePerMilliSecond());
                        // update GUI
                        musicPlayerGUI.setPlaybackSliderValue(calculatedFrame);
                        // mimic 1 millisecond using thread.sleep
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    @Override
    public void playbackStarted(PlaybackEvent evt) {
        // this method gets called in the beginning of the song
        System.out.println("Playback started");
        isFinished = false;
        pressNext = false;
        pressPrevious = false;
    }

    @Override
    public void playbackFinished(PlaybackEvent evt) {
        // this method gets called in the end of the song
        System.out.println("Playback finished");

        if (isPaused) {
            currentFrame += (int) ((double) evt.getFrame() * currentSong.getFrameRatePerMilliSecond());
        } else {
            // if the user pressed next or previous, we skip the rest
            if (pressNext || pressPrevious) {
                return;
            }
            // when the song ends
            isFinished = true;

            if (playList == null) {
                // update the GUI
                musicPlayerGUI.enablePlayButtonDisablePauseButton();
            } else {
                // last song in the playlist
                if (currentPlaylistIndex == playList.size() - 1) {
                    // update the GUI
                    musicPlayerGUI.enablePlayButtonDisablePauseButton();
                } else {
                    // go to the next song
                    nextSong();
                }
            }
        }
    }
}

