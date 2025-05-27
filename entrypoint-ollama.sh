#!/bin/sh
# Start ollama serve in the background
ollama serve &
# Capture PID of ollama serve
pid=$!

# Wait for a few seconds to ensure the server is up
# A more robust check would be to curl the API endpoint, but sleep is simpler for now.
echo "Waiting for Ollama server to start..."
sleep 15

echo "Pulling Llama 3.2 model..."
ollama pull llama3.2
echo "Llama 3 pull complete."

echo "Pulling Phi-4 model..."
ollama pull phi-4
echo "Phi-4 pull complete."

echo "Ollama server and model pulling setup complete. Ollama serve continues in background."
# Keep the script running by waiting for the background ollama serve process
wait $pid