#!/usr/bin/env python3
import matplotlib.pyplot as plt
import sys

def main():
    # Check command-line arguments
    if len(sys.argv) != 3:
        print("Usage: python3 plot_moe.py <input_file.txt> <output_figure.png>")
        sys.exit(1)

    input_file = sys.argv[1]  # Input data file (1st arg)
    output_fig = sys.argv[2]  # Output figure file (2nd arg)

    # Read data
    with open(input_file, 'r') as file:
        data = [list(map(float, line.strip().split())) for line in file if line.strip()]
        scenarios = [int(row[0]) for row in data]  # 1st col: scenario count
        min_moe = [row[1] for row in data]        # 2nd col: min %MOE
        avg_moe = [row[2] for row in data]        # 3rd col: avg %MOE
        max_moe = [row[3] for row in data]        # 4th col: max %MOE

    # Create plot
    plt.figure(figsize=(10, 6))
    plt.plot(scenarios, avg_moe, marker='o', linestyle='-', color='b', label='Avg %MOE')
    plt.fill_between(scenarios, min_moe, max_moe, color='b', alpha=0.2, label='Min-Max Range')

    plt.xlabel('Number of Scenarios')
    plt.ylabel('% Margin of Error (Relative to Mean)')
    plt.title('Margin of Error vs. Number of Scenarios')
    plt.grid(True, linestyle='--', alpha=0.6)
    plt.legend()

    # Save figure (instead of showing)
    plt.savefig(output_fig, dpi=300, bbox_inches='tight')
    print(f"Plot saved to {output_fig}")

if __name__ == "__main__":
    main()
