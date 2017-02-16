package ru.sash0k.bluetooth_terminal;

/**
 * Created by kbigler on 2/16/2017.
 */

public class GaugeState {
    private int myState = 3;
    private int lastState = 3;
    private ChangeListener listener;

    public int getGState() {
        return myState;
    }

    public void nextGState() {
        if (this.myState == 1 || this.myState == 2) {
            this.lastState = this.myState;
            this.myState = 0;
        } else if (this.myState == 0) {
            if (this.lastState == 1) {
                this.myState = 2;
            } else {
                this.myState = 1;
            }
        }
        if (listener != null) listener.onChange();
    }

    public void startGState () {
        this.lastState = 2;
        this.myState = 1;
        if (listener != null) listener.onChange();
    }

    public ChangeListener getListener() {
        return listener;
    }

    public void setListener(ChangeListener listener) {
        this.listener = listener;
    }

    public interface ChangeListener {
        void onChange();
    }
}