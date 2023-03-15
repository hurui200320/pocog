#define WORD_SIZE 32  // int has 32 bits

int get(__global const int *data, int x, int gameWidth) {
    if (x < 0 || x >= gameWidth) return false;
    int wordIndex = x / WORD_SIZE;
    int wordOffset = x % WORD_SIZE;
    int word = data[wordIndex];
    return word & (1 << wordOffset);
}

void set(__global int *data, int x, int gameWidth) {
    if (x < 0 || x >= gameWidth) return;
    int wordIndex = x / WORD_SIZE;
    int wordOffset = x % WORD_SIZE;
    int word = data[wordIndex] | (1 << wordOffset);
    data[wordIndex] = word;
}

void clear(__global int *data, int x, int gameWidth) {
    if (x < 0 || x >= gameWidth) return;
    int wordIndex = x / WORD_SIZE;
    int wordOffset = x % WORD_SIZE;
    int word = data[wordIndex] & ~(1 << wordOffset);
    data[wordIndex] = word;
}

int countCell(
    __global const int *top,
    __global const int *middle,
    __global const int *bottom,
    int x, int gameWidth
) {
    int result = 0;

    // count y-1
    if (get(top, x-1, gameWidth)) result++;
    if (get(top, x,   gameWidth)) result++;
    if (get(top, x+1, gameWidth)) result++;

    // count y
    if (get(middle, x-1, gameWidth)) result++;
    if (get(middle, x+1, gameWidth)) result++;

    // count y+1
    if (get(bottom, x-1, gameWidth)) result++;
    if (get(bottom, x,   gameWidth)) result++;
    if (get(bottom, x+1, gameWidth)) result++;

    return result;
}

void calculateRow(
    __global const int *top,
    __global const int *middle,
    __global const int *bottom,
    __global int *out,
    const int wordIndex,
    const int gameWidth
) {
    // here we will update the whole word, because of synchronization issues.
    // if we update 1 cell/bit per kernel, the update is not atomic,
    // thus the same word will conflict, generating bad result.
    // by updating the whole word in one kernel, we avoid conflict.
    int start = wordIndex * WORD_SIZE;

    for (int x = start; x < start + WORD_SIZE; x++) {
        if (x < 0 || x >= gameWidth) continue;

        int cell = get(middle, x, gameWidth);
        int count = countCell(top, middle, bottom, x, gameWidth);

        if (cell) {
            if (count == 2 || count == 3) {
                set(out, x, gameWidth);
            } else {
                clear(out, x, gameWidth);
            }
        } else {
            if (count == 3) {
                set(out, x, gameWidth);
            } else {
                clear(out, x, gameWidth);
            }
        }
    }
}

__kernel void calculateNextTick(
    %GENERATED_INPUT%
    %GENERATED_OUTPUT%
    const int gameWidth
) {
    int idx = get_global_id(0);

    %GENERATED_CALCULATION%
}
