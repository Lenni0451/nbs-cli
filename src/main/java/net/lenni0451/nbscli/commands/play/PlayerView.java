package net.lenni0451.nbscli.commands.play;

interface PlayerView extends AutoCloseable {

    void render();

    @Override
    void close();

}
