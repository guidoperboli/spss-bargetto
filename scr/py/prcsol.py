import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from pathlib import Path
from dataclasses import dataclass
from typing import List, Dict, Tuple
import glob

@dataclass
class SupplierInfo:
    id: int
    cost: float

@dataclass
class BinInfo:
    id: int
    supplier_id: int
    bin_type: int
    volume: float
    cost: float

@dataclass
class ItemGroup:
    item_count: int
    item_size: float
    bin_id: int

@dataclass
class SolutionData:
    instance_name: str
    selected_suppliers_count: int
    total_available_suppliers: int
    suppliers: List[SupplierInfo]
    bins: List[BinInfo]
    nr_items: int
    min_volume: float
    max_volume: float
    item_groups: List[ItemGroup]

@dataclass
class KPIResults:
    instance_name: str
    total_available_suppliers: int
    nr_items: int
    # KPI 1: Number of selected suppliers
    selected_suppliers_count: int
    # KPI 2: Number of used bin types
    used_bin_types_count: int
    # KPI 3: Number of bins used per supplier
    bins_per_supplier: Dict[int, int]
    # KPI 4: Total volume booked per supplier
    volume_per_supplier: Dict[int, float]
    # Additional useful metrics
    total_cost: float
    supplier_cost: float
    bin_cost: float
    total_volume_booked: float
    # Item statistics
    total_items_packed: int
    volume_utilization: float

def read_solution_file(filename: str) -> SolutionData:
    """
    Read the solution file with the corrected item format
    """
    instance_name = Path(filename).stem
    suppliers = []
    bins = []
    item_groups = []
    
    with open(filename, 'r') as f:
        # Read supplier counts (selected and total available)
        supplier_counts = f.readline().strip().split()
        selected_suppliers_count = int(supplier_counts[0])
        total_available_suppliers = int(supplier_counts[1])
        
        # Read selected suppliers
        for _ in range(selected_suppliers_count):
            line = f.readline().strip().split()
            suppliers.append(SupplierInfo(id=int(line[0]), cost=float(line[1])))
        
        # Read bin selection count
        bin_count = int(f.readline().strip())
        
        # Read selected bins
        for _ in range(bin_count):
            line = f.readline().strip().split()
            bins.append(BinInfo(
                id=int(line[0]),
                supplier_id=int(line[1]),
                bin_type=int(line[2]),
                volume=float(line[3]),
                cost=float(line[4])
            ))
        
        # Read item information
        item_info = f.readline().strip().split()
        nr_items = int(item_info[0])
        min_volume = float(item_info[1])
        max_volume = float(item_info[2])
        
        # Read item groups (item_count, item_size, bin_id)
        for line in f:
            if line.strip():
                parts = line.strip().split()
                item_groups.append(ItemGroup(
                    item_count=int(parts[0]),
                    item_size=float(parts[1]),
                    bin_id=int(parts[2])
                ))
    
    return SolutionData(
        instance_name=instance_name,
        selected_suppliers_count=selected_suppliers_count,
        total_available_suppliers=total_available_suppliers,
        suppliers=suppliers,
        bins=bins,
        nr_items=nr_items,
        min_volume=min_volume,
        max_volume=max_volume,
        item_groups=item_groups
    )

def analyze_bin_type_usage(solution: SolutionData) -> Dict[int, Dict]:
    """
    Analyze bin usage statistics per bin type
    Returns: Dict[bin_type, statistics_dict]
    """
    bin_type_stats = {}
    
    # Group bins by type
    for bin_info in solution.bins:
        bin_type = bin_info.bin_type
        if bin_type not in bin_type_stats:
            bin_type_stats[bin_type] = {
                'bin_type': bin_type,
                'bin_volume': bin_info.volume,  # Assuming same volume for same type
                'count': 0,
                'total_volume': 0,
                'total_cost': 0,
                'suppliers_used': set(),
                'bins_used': []  # List of bin IDs for this type
            }
        
        stats = bin_type_stats[bin_type]
        stats['count'] += 1
        stats['total_volume'] += bin_info.volume
        stats['total_cost'] += bin_info.cost
        stats['suppliers_used'].add(bin_info.supplier_id)
        stats['bins_used'].append(bin_info.id)
    
    # Calculate item packing statistics for each bin type
    for bin_type, stats in bin_type_stats.items():
        # Get all bins of this type
        bin_ids = stats['bins_used']
        
        # Calculate items packed in these bins
        items_in_bin_type = []
        total_item_volume = 0
        total_items = 0
        
        for item_group in solution.item_groups:
            if item_group.bin_id in bin_ids:
                items_in_bin_type.append(item_group)
                total_item_volume += item_group.item_count * item_group.item_size
                total_items += item_group.item_count
        
        stats['total_items_packed'] = total_items
        stats['total_item_volume'] = total_item_volume
        stats['volume_utilization'] = (total_item_volume / stats['total_volume']) if stats['total_volume'] > 0 else 0
        stats['avg_items_per_bin'] = total_items / stats['count'] if stats['count'] > 0 else 0
        stats['suppliers_used'] = list(stats['suppliers_used'])
        stats['supplier_count'] = len(stats['suppliers_used'])
        
        # Remove the bins_used list to clean up output
        del stats['bins_used']
    
    return bin_type_stats

def analyze_bin_types_across_instances(df: pd.DataFrame, solutions_dict: Dict[str, SolutionData]) -> pd.DataFrame:
    """
    Analyze bin type usage patterns across all instances
    """
    all_bin_type_data = []
    
    for instance_name, solution in solutions_dict.items():
        bin_type_stats = analyze_bin_type_usage(solution)
        
        # Calculate total volume booked for this instance
        total_instance_volume = sum(bin_info.volume for bin_info in solution.bins)
        
        for bin_type, stats in bin_type_stats.items():
            # Calculate percentage of total instance volume
            volume_percentage = (stats['total_volume'] / total_instance_volume) if total_instance_volume > 0 else 0
            
            row = {
                'instance_name': instance_name,
                'total_available_suppliers': solution.total_available_suppliers,
                'nr_items': solution.nr_items,
                'bin_type': bin_type,
                'bin_volume': stats['bin_volume'],
                'bin_count': stats['count'],
                'total_volume_capacity': stats['total_volume'],
                'volume_percentage_of_total': volume_percentage,  # NEW COLUMN
                'total_cost': stats['total_cost'],
                'total_items_packed': stats['total_items_packed'],
                'total_item_volume': stats['total_item_volume'],
                'volume_utilization_percent': stats['volume_utilization'],
                'avg_items_per_bin': stats['avg_items_per_bin'],
                'supplier_count': stats['supplier_count'],
                'suppliers_used': ', '.join(map(str, stats['suppliers_used']))
            }
            all_bin_type_data.append(row)
    
    return pd.DataFrame(all_bin_type_data)

def generate_bin_type_aggregates(bin_type_df: pd.DataFrame) -> pd.DataFrame:
    """
    Generate aggregated statistics for bin types across all instances
    """
    if bin_type_df.empty:
        return pd.DataFrame()
    
    aggregated = bin_type_df.groupby(['bin_type', 'bin_volume']).agg({
        'instance_name': 'count',  # Number of instances using this bin type
        'bin_count': ['mean', 'std', 'min', 'max'],
        'total_volume_capacity': ['mean', 'std'],
        'volume_percentage_of_total': ['mean', 'std', 'min', 'max'],  # Include new column in aggregates
        'total_cost': ['mean', 'std'],
        'total_items_packed': ['mean', 'std'],
        'volume_utilization_percent': ['mean', 'std', 'min', 'max'],
        'avg_items_per_bin': ['mean', 'std'],
        'supplier_count': ['mean', 'max']
    }).round(2)
    
    # Flatten column names
    aggregated.columns = ['_'.join(col).strip() for col in aggregated.columns.values]
    aggregated = aggregated.reset_index()
    
    # Rename columns for clarity
    aggregated = aggregated.rename(columns={
        'instance_name_count': 'instances_used_count',
        'bin_count_mean': 'avg_bins_per_instance',
        'bin_count_std': 'std_bins_per_instance',
        'bin_count_min': 'min_bins_per_instance',
        'bin_count_max': 'max_bins_per_instance',
        'volume_percentage_of_total_mean': 'avg_volume_percentage',
        'volume_percentage_of_total_std': 'std_volume_percentage',
        'volume_percentage_of_total_min': 'min_volume_percentage',
        'volume_percentage_of_total_max': 'max_volume_percentage'
    })
    
    return aggregated

def create_synthesis_analysis(df: pd.DataFrame, bin_type_df: pd.DataFrame):
    """
    Create comprehensive synthesis analysis files
    """
    # 1. Solution Structure Synthesis
    synthesis_data = []
    
    for _, instance in df.iterrows():
        instance_name = instance['instance_name']
        
        # Get bin type data for this instance
        instance_bins = bin_type_df[bin_type_df['instance_name'] == instance_name]
        
        # Count active suppliers for this instance (suppliers that actually provide bins)
        active_suppliers = len([col for col in instance.index 
                              if col.startswith('bins_supplier_') 
                              and pd.notna(instance[col]) 
                              and instance[col] > 0])
        
        # Structural characteristics
        row = {
            'instance': instance_name,
            'selected_suppliers': instance['selected_suppliers_count'],
            'active_suppliers': active_suppliers,
            'suppliers_over_minimum': active_suppliers - 2,  # Beyond mandatory minimum
            'bin_types_used': instance['used_bin_types_count'],
            'total_bins': instance_bins['bin_count'].sum(),
            'total_cost': instance['total_cost'],
            'volume_utilization': instance['volume_utilization_percent'],
            
            # Bin type concentration
            'dominant_bin_type': instance_bins.loc[instance_bins['volume_percentage_of_total'].idxmax(), 'bin_type'] if not instance_bins.empty else None,
            'dominant_type_share': instance_bins['volume_percentage_of_total'].max() if not instance_bins.empty else 0,
            'bin_type_mix': len(instance_bins),
            'has_multiple_types': len(instance_bins) > 1
        }
        
        synthesis_data.append(row)
    
    synthesis_df = pd.DataFrame(synthesis_data)
    
    # 2. Key Patterns Summary
    summary_stats = {
        'metric': [
            'Selected Suppliers (always)',
            'Active Suppliers (avg)',
            'Suppliers Beyond Minimum (avg)',
            'Bin Types Used (avg)',
            'Instances with >2 Bin Types',
            'Dominant Bin Type Share (avg)',
            'Total Cost (avg)',
            'Volume Utilization (avg)',
            'Total Bins (avg)'
        ],
        'value': [
            f"{synthesis_df['selected_suppliers'].mean():.0f}",
            f"{synthesis_df['active_suppliers'].mean():.1f}",
            f"{synthesis_df['suppliers_over_minimum'].mean():.1f}",
            f"{synthesis_df['bin_types_used'].mean():.1f}",
            f"{(synthesis_df['bin_types_used'] > 2).sum() / len(synthesis_df):.0%}",
            f"{synthesis_df['dominant_type_share'].mean():.1%}",
            f"{synthesis_df['total_cost'].mean():.0f}",
            f"{synthesis_df['volume_utilization'].mean():.1%}",
            f"{synthesis_df['total_bins'].mean():.1f}"
        ]
    }
    
    summary_df = pd.DataFrame(summary_stats)
    
    # 3. Bin Type Efficiency Analysis
    bin_efficiency = bin_type_df.groupby(['bin_type', 'bin_volume']).agg({
        'instance_name': 'count',
        'volume_percentage_of_total': 'mean',
        'volume_utilization_percent': 'mean',
        'avg_items_per_bin': 'mean',
        'bin_count': 'mean'
    }).round(3).reset_index()
    
    bin_efficiency = bin_efficiency.rename(columns={
        'instance_name': 'usage_frequency',
        'volume_percentage_of_total': 'avg_volume_share',
        'volume_utilization_percent': 'avg_utilization',
        'avg_items_per_bin': 'avg_items_per_bin',
        'bin_count': 'avg_bins_when_used'
    })
    
    # Calculate efficiency metrics
    bin_efficiency['items_per_volume'] = bin_efficiency['avg_items_per_bin'] / bin_efficiency['bin_volume']
    bin_efficiency['usage_rate'] = bin_efficiency['usage_frequency'] / len(bin_type_df['instance_name'].unique())
    
    # 4. Solution Archetypes Analysis
    instance_bin_mix = bin_type_df.groupby('instance_name').agg({
        'bin_type': lambda x: tuple(sorted(x)),
        'bin_count': 'sum'
    }).reset_index()
    
    archetypes = instance_bin_mix['bin_type'].value_counts().reset_index()
    archetypes.columns = ['bin_type_combo', 'frequency']
    archetypes['percentage'] = (archetypes['frequency'] / len(instance_bin_mix) * 100).round(1)
    
    return synthesis_df, summary_df, bin_efficiency, archetypes

def create_synthesis_visualizations(synthesis_df, bin_efficiency, archetypes, output_dir="plots"):
    """
    Create visualizations for synthesis analysis
    """
    Path(output_dir).mkdir(exist_ok=True)
    
    # 1. Bin Type Efficiency Comparison
    plt.figure(figsize=(10, 6))
    x_pos = np.arange(len(bin_efficiency))
    width = 0.35
    
    fig, ax1 = plt.subplots(figsize=(12, 6))
    
    color = 'tab:blue'
    ax1.set_xlabel('Bin Type')
    ax1.set_ylabel('Items per Volume Unit', color=color)
    bars1 = ax1.bar(x_pos - width/2, bin_efficiency['items_per_volume'], width, 
                   label='Items/Volume', color=color, alpha=0.7)
    ax1.tick_params(axis='y', labelcolor=color)
    
    ax2 = ax1.twinx()
    color = 'tab:red'
    ax2.set_ylabel('Usage Rate', color=color)
    bars2 = ax2.bar(x_pos + width/2, bin_efficiency['usage_rate'], width, 
                   label='Usage Rate', color=color, alpha=0.7)
    ax2.tick_params(axis='y', labelcolor=color)
    
    plt.xticks(x_pos, [f'Type {t}\n({v}L)' for t, v in zip(bin_efficiency['bin_type'], bin_efficiency['bin_volume'])])
    plt.title('Bin Type Efficiency and Usage Patterns')
    fig.tight_layout()
    plt.savefig(f'{output_dir}/bin_type_efficiency_comparison.png', dpi=300, bbox_inches='tight')
    plt.close()
    
    # 2. Solution Archetypes
    plt.figure(figsize=(10, 6))
    plt.bar(archetypes['bin_type_combo'].astype(str), archetypes['percentage'], alpha=0.7)
    plt.xlabel('Bin Type Combination')
    plt.ylabel('Percentage of Instances (%)')
    plt.title('Solution Archetypes - Bin Type Combinations')
    plt.xticks(rotation=45, ha='right')
    plt.tight_layout()
    plt.savefig(f'{output_dir}/solution_archetypes.png', dpi=300, bbox_inches='tight')
    plt.close()
    
    # 3. Supplier Activity vs Bin Types
    plt.figure(figsize=(10, 6))
    plt.scatter(synthesis_df['active_suppliers'], synthesis_df['bin_types_used'], 
               alpha=0.6, s=100, c=synthesis_df['total_cost'], cmap='viridis')
    plt.xlabel('Active Suppliers')
    plt.ylabel('Bin Types Used')
    plt.title('Supplier Activity vs Bin Type Diversity (color = cost)')
    plt.colorbar(label='Total Cost')
    plt.grid(True, alpha=0.3)
    plt.savefig(f'{output_dir}/supplier_vs_bintype_diversity.png', dpi=300, bbox_inches='tight')
    plt.close()

def print_bin_type_summary(bin_type_aggregates: pd.DataFrame):
    """
    Print a summary of bin type analysis
    """
    print("\n=== BIN TYPE ANALYSIS SUMMARY ===")
    if bin_type_aggregates.empty:
        print("No bin type data available.")
        return
    
    for _, row in bin_type_aggregates.iterrows():
        print(f"\nBin Type {row['bin_type']} (Volume: {row['bin_volume']}):")
        print(f"  Used in {row['instances_used_count']} instances")
        print(f"  Average bins per instance: {row['avg_bins_per_instance']:.2f}")
        print(f"  Average volume percentage: {row['avg_volume_percentage']:.2f}%")
        print(f"  Average utilization: {row['volume_utilization_percent_mean']:.2f}%")
        print(f"  Average items per bin: {row['avg_items_per_bin_mean']:.2f}")
        print(f"  Average cost per instance: {row['total_cost_mean']:.2f}")

def calculate_kpis(solution: SolutionData) -> KPIResults:
    """
    Calculate all KPIs for a single solution
    """
    # KPI 1: Number of selected suppliers (already available)
    selected_suppliers_count = solution.selected_suppliers_count
    
    # KPI 2: Number of used bin types
    used_bin_types = set(bin.bin_type for bin in solution.bins)
    used_bin_types_count = len(used_bin_types)
    
    # KPI 3: Number of bins used per supplier
    bins_per_supplier = {}
    for bin_info in solution.bins:
        bins_per_supplier[bin_info.supplier_id] = bins_per_supplier.get(bin_info.supplier_id, 0) + 1
    
    # KPI 4: Total volume booked per supplier
    volume_per_supplier = {}
    for bin_info in solution.bins:
        supplier_id = bin_info.supplier_id
        volume_per_supplier[supplier_id] = volume_per_supplier.get(supplier_id, 0) + bin_info.volume
    
    # Cost calculations
    supplier_cost = sum(s.cost for s in solution.suppliers)
    bin_cost = sum(b.cost for b in solution.bins)
    total_cost = supplier_cost + bin_cost
    
    # Total volume booked
    total_volume_booked = sum(bin_info.volume for bin_info in solution.bins)
    
    # Item statistics
    total_items_packed = sum(group.item_count for group in solution.item_groups)
    
    # Calculate volume utilization (total item volume / total bin volume)
    total_item_volume = sum(group.item_count * group.item_size for group in solution.item_groups)
    volume_utilization = (total_item_volume / total_volume_booked) if total_volume_booked > 0 else 0
    
    return KPIResults(
        instance_name=solution.instance_name,
        total_available_suppliers=solution.total_available_suppliers,
        nr_items=solution.nr_items,
        selected_suppliers_count=selected_suppliers_count,
        used_bin_types_count=used_bin_types_count,
        bins_per_supplier=bins_per_supplier,
        volume_per_supplier=volume_per_supplier,
        total_cost=total_cost,
        supplier_cost=supplier_cost,
        bin_cost=bin_cost,
        total_volume_booked=total_volume_booked,
        total_items_packed=total_items_packed,
        volume_utilization=volume_utilization
    )

def process_multiple_instances(file_pattern: str) -> pd.DataFrame:
    """
    Process multiple instance files and return aggregated results
    """
    all_kpis = []
    
    for filepath in glob.glob(file_pattern):
        try:
            print(f"Processing: {filepath}")
            solution = read_solution_file(filepath)
            kpis = calculate_kpis(solution)
            
            # Convert to DataFrame row
            kpi_row = {
                'instance_name': kpis.instance_name,
                'total_available_suppliers': kpis.total_available_suppliers,
                'nr_items': kpis.nr_items,
                'selected_suppliers_count': kpis.selected_suppliers_count,
                'used_bin_types_count': kpis.used_bin_types_count,
                'total_cost': kpis.total_cost,
                'supplier_cost': kpis.supplier_cost,
                'bin_cost': kpis.bin_cost,
                'total_volume_booked': kpis.total_volume_booked,
                'total_items_packed': kpis.total_items_packed,
                'volume_utilization_percent': kpis.volume_utilization
            }
            
            # Add supplier-specific KPIs
            for supplier_id, bin_count in kpis.bins_per_supplier.items():
                kpi_row[f'bins_supplier_{supplier_id}'] = bin_count
                kpi_row[f'volume_supplier_{supplier_id}'] = kpis.volume_per_supplier.get(supplier_id, 0)
            
            all_kpis.append(kpi_row)
            
        except Exception as e:
            print(f"Error processing {filepath}: {e}")
    
    return pd.DataFrame(all_kpis)

def generate_aggregated_statistics(df: pd.DataFrame) -> pd.DataFrame:
    """
    Generate aggregated statistics grouped by available suppliers and number of items
    """
    if df.empty:
        return pd.DataFrame()
    
    # Group by available suppliers and number of items
    grouped = df.groupby(['total_available_suppliers', 'nr_items']).agg({
        'selected_suppliers_count': ['count', 'mean', 'std', 'min', 'max'],
        'used_bin_types_count': ['mean', 'std', 'min', 'max'],
        'total_cost': ['mean', 'std', 'min', 'max'],
        'supplier_cost': ['mean', 'std'],
        'bin_cost': ['mean', 'std'],
        'total_volume_booked': ['mean', 'std'],
        'volume_utilization_percent': ['mean', 'std', 'min', 'max'],
        'total_items_packed': ['mean', 'std']
    }).round(2)
    
    # Flatten column names
    grouped.columns = ['_'.join(col).strip() for col in grouped.columns.values]
    grouped = grouped.reset_index()
    
    return grouped

def analyze_supplier_usage(df: pd.DataFrame) -> pd.DataFrame:
    """
    Analyze supplier usage patterns across instances
    """
    supplier_columns = [col for col in df.columns if col.startswith('bins_supplier_')]
    
    supplier_analysis = []
    for col in supplier_columns:
        supplier_id = col.replace('bins_supplier_', '')
        volume_col = f'volume_supplier_{supplier_id}'
        
        if volume_col in df.columns:
            # Calculate usage statistics
            bins_data = df[col].dropna()
            volume_data = df[volume_col].dropna()
            
            supplier_stats = {
                'supplier_id': supplier_id,
                'usage_frequency': len(bins_data),
                'usage_rate': len(bins_data) / len(df),
                'avg_bins_when_used': bins_data.mean(),
                'avg_volume_when_used': volume_data.mean(),
                'total_volume_across_instances': volume_data.sum()
            }
            supplier_analysis.append(supplier_stats)
    
    return pd.DataFrame(supplier_analysis)

def create_visualizations(df: pd.DataFrame, output_dir: str = "plots"):
    """
    Create visualizations for the aggregated KPIs
    """
    Path(output_dir).mkdir(exist_ok=True)
    
    # Set style
    plt.style.use('default')
    sns.set_palette("husl")
    
    # 1. Selected suppliers vs available suppliers
    plt.figure(figsize=(10, 6))
    plt.scatter(df['total_available_suppliers'], df['selected_suppliers_count'], alpha=0.6)
    plt.plot([df['total_available_suppliers'].min(), df['total_available_suppliers'].max()], 
             [df['total_available_suppliers'].min(), df['total_available_suppliers'].max()], 
             'r--', alpha=0.5, label='Maximum possible')
    plt.xlabel('Total Available Suppliers')
    plt.ylabel('Selected Suppliers Count')
    plt.title('Selected Suppliers vs Available Suppliers')
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.savefig(f'{output_dir}/selected_vs_available_suppliers.png', dpi=300, bbox_inches='tight')
    plt.close()
    
    # 2. Volume utilization distribution
    plt.figure(figsize=(8, 6))
    plt.hist(df['volume_utilization_percent'], bins=20, alpha=0.7, color='skyblue')
    plt.xlabel('Volume Utilization (%)')
    plt.ylabel('Number of Instances')
    plt.title('Distribution of Volume Utilization')
    plt.grid(True, alpha=0.3)
    plt.savefig(f'{output_dir}/volume_utilization.png', dpi=300, bbox_inches='tight')
    plt.close()
    
    # 3. Bin types usage
    plt.figure(figsize=(8, 6))
    bin_types_counts = df['used_bin_types_count'].value_counts().sort_index()
    plt.bar(bin_types_counts.index, bin_types_counts.values, alpha=0.7)
    plt.xlabel('Number of Bin Types Used')
    plt.ylabel('Number of Instances')
    plt.title('Distribution of Bin Types Usage')
    plt.grid(True, alpha=0.3)
    plt.savefig(f'{output_dir}/bin_types_usage.png', dpi=300, bbox_inches='tight')
    plt.close()

def generate_summary_report(df: pd.DataFrame, output_file: str = "summary_report.txt"):
    """
    Generate a text summary report
    """
    with open(output_file, 'w') as f:
        f.write("=== PACKING SOLUTION ANALYSIS SUMMARY ===\n\n")
        f.write(f"Total instances processed: {len(df)}\n")
        f.write(f"Available suppliers range: {df['total_available_suppliers'].min()} - {df['total_available_suppliers'].max()}\n")
        f.write(f"Items range: {df['nr_items'].min()} - {df['nr_items'].max()}\n\n")
        
        f.write("KEY METRICS (averages):\n")
        f.write(f"Selected suppliers: {df['selected_suppliers_count'].mean():.2f}\n")
        f.write(f"Bin types used: {df['used_bin_types_count'].mean():.2f}\n")
        f.write(f"Total cost: {df['total_cost'].mean():.2f}\n")
        f.write(f"Volume utilization: {df['volume_utilization_percent'].mean():.2f}%\n")
        f.write(f"Items packed: {df['total_items_packed'].mean():.2f}\n\n")
        
        # Supplier usage summary
        supplier_columns = [col for col in df.columns if col.startswith('bins_supplier_')]
        f.write(f"Suppliers encountered: {len(supplier_columns)}\n")

def main():
    """
    Main function to process all instances and generate reports
    """
    # Process all solution files
    print("Processing solution files...")
    df = process_multiple_instances("cpssp_*.sspsol")  # Adjust pattern as needed
    
    if df.empty:
        print("No files processed. Check your file pattern.")
        return

    # Read all solutions into a dictionary for bin type analysis
    solutions_dict = {}
    for filepath in glob.glob("cpssp_*.sspsol"):
        try:
            instance_name = Path(filepath).stem
            solutions_dict[instance_name] = read_solution_file(filepath)
        except Exception as e:
            print(f"Error reading {filepath} for bin analysis: {e}")
        
    # Save detailed results
    df.to_csv('detailed_kpi_results.csv', index=False)
    print(f"Processed {len(df)} instances. Detailed results saved to 'detailed_kpi_results.csv'")
    
    # Generate aggregated statistics
    aggregated_stats = generate_aggregated_statistics(df)
    if not aggregated_stats.empty:
        aggregated_stats.to_csv('aggregated_statistics.csv', index=False)
        print("Aggregated statistics saved to 'aggregated_statistics.csv'")
        print("\nAggregated Statistics:")
        print(aggregated_stats.to_string(index=False))
    
    # Analyze supplier usage
    supplier_analysis = analyze_supplier_usage(df)
    if not supplier_analysis.empty:
        supplier_analysis.to_csv('supplier_analysis.csv', index=False)
        print("\nSupplier Analysis:")
        print(supplier_analysis.to_string(index=False))

    if solutions_dict:
        print("\n=== ANALYZING BIN TYPES ===")
        bin_type_df = analyze_bin_types_across_instances(df, solutions_dict)
        if not bin_type_df.empty:
            bin_type_df.to_csv('bin_type_analysis_detailed.csv', index=False)
            print("Detailed bin type analysis saved to 'bin_type_analysis_detailed.csv'")
            
            bin_type_aggregates = generate_bin_type_aggregates(bin_type_df)
            if not bin_type_aggregates.empty:
                bin_type_aggregates.to_csv('bin_type_aggregates.csv', index=False)
                print("Bin type aggregates saved to 'bin_type_aggregates.csv'")
                
                print_bin_type_summary(bin_type_aggregates)
                
                # NEW: SYNTHESIS ANALYSIS
                print("\n=== GENERATING SYNTHESIS ANALYSIS ===")
                synthesis_df, summary_df, bin_efficiency, archetypes = create_synthesis_analysis(df, bin_type_df)
                
                # Save synthesis files
                synthesis_df.to_csv('solution_structure_synthesis.csv', index=False)
                summary_df.to_csv('key_patterns_summary.csv', index=False)
                bin_efficiency.to_csv('bin_type_efficiency_analysis.csv', index=False)
                archetypes.to_csv('solution_archetypes.csv', index=False)
                
                print("Synthesis analysis saved:")
                print("  - solution_structure_synthesis.csv")
                print("  - key_patterns_summary.csv") 
                print("  - bin_type_efficiency_analysis.csv")
                print("  - solution_archetypes.csv")
                
                # Create synthesis visualizations
                create_synthesis_visualizations(synthesis_df, bin_efficiency, archetypes)
                print("Synthesis visualizations saved to 'plots/' directory")
                
                # Print key insights
                print("\n=== KEY STRUCTURAL INSIGHTS ===")
                print(f"• Solutions always select exactly 2 suppliers (minimum constraint)")
                print(f"• Average of {synthesis_df['bin_types_used'].mean():.1f} bin types used per instance")
                print(f"• {((synthesis_df['bin_types_used'] > 2).sum() / len(synthesis_df)):.0%} of instances use >2 bin types")
                print(f"• Dominant bin type typically occupies {synthesis_df['dominant_type_share'].mean():.0%} of total volume")
                print(f"• Volume utilization: {synthesis_df['volume_utilization'].mean():.1%}")
    
    # Create other visualizations
    create_visualizations(df)
    print("\nVisualizations saved to 'plots/' directory")
    
    # Generate summary report
    generate_summary_report(df)
    print("Summary report saved to 'summary_report.txt'")
    
    # Print quick summary
    print(f"\n=== QUICK SUMMARY ===")
    print(f"Total instances: {len(df)}")
    print(f"Avg selected suppliers: {df['selected_suppliers_count'].mean():.2f}")
    print(f"Avg bin types: {df['used_bin_types_count'].mean():.2f}")
    print(f"Avg volume utilization: {df['volume_utilization_percent'].mean():.2f}%")

if __name__ == "__main__":
    main()
