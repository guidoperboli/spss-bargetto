import pandas as pd
import numpy as np
from pathlib import Path
from typing import Dict, List
import glob

def read_solution_file(filename: str) -> Dict:
    """
    Read solution file and extract raw data for KPI computation
    """
    instance_name = Path(filename).stem
    
    with open(filename, 'r') as f:
        # Read supplier counts (selected and total available)
        supplier_counts = f.readline().strip().split()
        selected_suppliers_count = int(supplier_counts[0])
        total_available_suppliers = int(supplier_counts[1])
        
        # Read selected suppliers
        suppliers = []
        for _ in range(selected_suppliers_count):
            line = f.readline().strip().split()
            suppliers.append({
                'id': int(line[0]),
                'cost': float(line[1])
            })
        
        # Read bin selection count
        bin_count = int(f.readline().strip())
        
        # Read selected bins
        bins = []
        for _ in range(bin_count):
            line = f.readline().strip().split()
            bins.append({
                'id': int(line[0]),
                'supplier_id': int(line[1]),
                'bin_type': int(line[2]),
                'volume': float(line[3]),
                'cost': float(line[4])
            })
        
        # Read item information
        item_info = f.readline().strip().split()
        nr_items = int(item_info[0])
        min_volume = float(item_info[1])
        max_volume = float(item_info[2])
        
        # Read item groups (item_count, item_size, bin_id)
        item_groups = []
        for line in f:
            if line.strip():
                parts = line.strip().split()
                item_groups.append({
                    'item_count': int(parts[0]),
                    'item_size': float(parts[1]),
                    'bin_id': int(parts[2])
                })
    
    return {
        'instance_name': instance_name,
        'total_available_suppliers': total_available_suppliers,
        'nr_items': nr_items,
        'suppliers': suppliers,
        'bins': bins,
        'item_groups': item_groups
    }

def calculate_kpis(solution_data: Dict) -> Dict:
    """
    Calculate the requested KPIs for a single solution
    """
    instance_name = solution_data['instance_name']
    bins = solution_data['bins']
    item_groups = solution_data['item_groups']
    
    # KPI 1: Number of bin types used
    bin_types_used = len(set(bin['bin_type'] for bin in bins))
    
    # Calculate supplier volume shares
    supplier_volumes = {}
    supplier_bin_types = {}  # Track bin types per supplier
    for bin in bins:
        supplier_id = bin['supplier_id']
        supplier_volumes[supplier_id] = supplier_volumes.get(supplier_id, 0) + bin['volume']
        if supplier_id not in supplier_bin_types:
            supplier_bin_types[supplier_id] = set()
        supplier_bin_types[supplier_id].add(bin['bin_type'])
    
    total_volume = sum(supplier_volumes.values())
    
    # KPI 2: Share of booked capacity for the dominant supplier
    if supplier_volumes:
        max_supplier_volume = max(supplier_volumes.values())
        dominant_supplier_share = (max_supplier_volume / total_volume) * 100 if total_volume > 0 else 0
        dominant_supplier_id = max(supplier_volumes, key=supplier_volumes.get)
    else:
        dominant_supplier_share = 0
        dominant_supplier_id = None
    
    # NEW: Supplier concentration index (Herfindahl)
    supplier_shares = [vol/total_volume for vol in supplier_volumes.values()] if total_volume > 0 else []
    supplier_concentration = sum(share ** 2 for share in supplier_shares) if supplier_shares else 0
    
    # NEW: Dominant supplier bin variety
    dominant_supplier_bin_variety = len(supplier_bin_types.get(dominant_supplier_id, set())) if dominant_supplier_id else 0
    
    # Calculate bin type statistics
    bin_type_volumes = {}
    bin_type_counts = {}
    bin_type_single_volume = {}  # Store volume of a single bin for each type
    
    for bin in bins:
        bin_type = bin['bin_type']
        bin_type_volumes[bin_type] = bin_type_volumes.get(bin_type, 0) + bin['volume']
        bin_type_counts[bin_type] = bin_type_counts.get(bin_type, 0) + 1
        # Store the volume of a single bin (assuming same volume for same type)
        bin_type_single_volume[bin_type] = bin['volume']
    
    # KPI 3: Share of the dominant bin type
    if bin_type_volumes:
        max_bin_type_volume = max(bin_type_volumes.values())
        dominant_bin_type_share = (max_bin_type_volume / total_volume) * 100 if total_volume > 0 else 0
        dominant_bin_type = max(bin_type_volumes, key=bin_type_volumes.get)
    else:
        dominant_bin_type_share = 0
        dominant_bin_type = None
    
    # KPI 4: Volume of a single bin of the dominant bin type
    dominant_bin_single_volume = bin_type_single_volume.get(dominant_bin_type, 0) if dominant_bin_type is not None else 0
    
    # NEW: Bin type concentration index (Herfindahl)
    bin_type_shares = [vol/total_volume for vol in bin_type_volumes.values()] if total_volume > 0 else []
    bin_type_concentration = sum(share ** 2 for share in bin_type_shares) if bin_type_shares else 0
    
    # Calculate items packed per bin type
    bin_type_items = {}
    
    # Create mapping from bin_id to bin_type
    bin_id_to_type = {bin['id']: bin['bin_type'] for bin in bins}
    
    for item_group in item_groups:
        bin_id = item_group['bin_id']
        bin_type = bin_id_to_type.get(bin_id)
        if bin_type is not None:
            bin_type_items[bin_type] = bin_type_items.get(bin_type, 0) + item_group['item_count']
    
    total_items_packed = sum(bin_type_items.values())
    
    # KPI 5: Percentage of items packed into the dominant bin type
    if dominant_bin_type is not None and total_items_packed > 0:
        items_in_dominant_bin = bin_type_items.get(dominant_bin_type, 0)
        items_in_dominant_bin_percentage = (items_in_dominant_bin / total_items_packed) * 100
    else:
        items_in_dominant_bin_percentage = 0
    
    # NEW: Dominant bin utilization efficiency
    if dominant_bin_type_share > 0:
        dominant_bin_utilization_rate = items_in_dominant_bin_percentage / dominant_bin_type_share
    else:
        dominant_bin_utilization_rate = 0
    
    # NEW: Supplier-bin type interaction - does dominant supplier provide dominant bin type?
    dominant_supplier_provides_dominant_bin = False
    if dominant_supplier_id is not None and dominant_bin_type is not None:
        # Check if dominant supplier provides bins of dominant type
        supplier_bins = [bin for bin in bins if bin['supplier_id'] == dominant_supplier_id]
        supplier_bin_types_set = set(bin['bin_type'] for bin in supplier_bins)
        dominant_supplier_provides_dominant_bin = dominant_bin_type in supplier_bin_types_set
    
    return {
        'instance_name': instance_name,
        'total_available_suppliers': solution_data['total_available_suppliers'],
        'nr_items': solution_data['nr_items'],
        'bin_types_used': bin_types_used,
        'dominant_supplier_share_percent': dominant_supplier_share,
        'dominant_supplier_id': dominant_supplier_id,
        'dominant_bin_type_share_percent': dominant_bin_type_share,
        'dominant_bin_type': dominant_bin_type,
        'dominant_bin_single_volume': dominant_bin_single_volume,
        'items_in_dominant_bin_percent': items_in_dominant_bin_percentage,
        'total_volume_booked': total_volume,
        'total_items_packed': total_items_packed,
        # NEW ENHANCED KPIs
        'supplier_concentration_index': supplier_concentration,
        'bin_type_concentration_index': bin_type_concentration,
        'dominant_bin_utilization_rate': dominant_bin_utilization_rate,
        'dominant_supplier_bin_variety': dominant_supplier_bin_variety,
        'dominant_supplier_provides_dominant_bin': dominant_supplier_provides_dominant_bin
    }

def process_all_instances(file_pattern: str) -> pd.DataFrame:
    """
    Process all solution files and compute KPIs
    """
    all_kpis = []
    
    for filepath in glob.glob(file_pattern):
        try:
            print(f"Processing: {filepath}")
            solution_data = read_solution_file(filepath)
            kpis = calculate_kpis(solution_data)
            all_kpis.append(kpis)
            
        except Exception as e:
            print(f"Error processing {filepath}: {e}")
    
    return pd.DataFrame(all_kpis)

def generate_aggregated_kpis(instance_kpis: pd.DataFrame) -> pd.DataFrame:
    """
    Generate aggregated KPIs grouped by available suppliers and number of items
    """
    if instance_kpis.empty:
        return pd.DataFrame()
    
    # Define aggregation functions for each KPI
    aggregation_config = {
        'bin_types_used': ['mean', 'std', 'min', 'max'],
        'dominant_supplier_share_percent': ['mean', 'std', 'min', 'max'],
        'dominant_bin_type_share_percent': ['mean', 'std', 'min', 'max'],
        'dominant_bin_single_volume': ['mean', 'std', 'min', 'max'],
        'items_in_dominant_bin_percent': ['mean', 'std', 'min', 'max'],
        'total_volume_booked': ['mean', 'std'],
        'total_items_packed': ['mean', 'std'],
        # NEW: Enhanced KPIs aggregation
        'supplier_concentration_index': ['mean', 'std', 'min', 'max'],
        'bin_type_concentration_index': ['mean', 'std', 'min', 'max'],
        'dominant_bin_utilization_rate': ['mean', 'std', 'min', 'max'],
        'dominant_supplier_bin_variety': ['mean', 'std', 'min', 'max'],
        'dominant_supplier_provides_dominant_bin': 'mean',  # Percentage of True values
        'instance_name': 'count'  # Number of instances in each group
    }
    
    # Group by available suppliers and number of items
    grouped = instance_kpis.groupby(['total_available_suppliers', 'nr_items']).agg(aggregation_config).round(3)
    
    # Flatten column names
    grouped.columns = ['_'.join(col).strip() for col in grouped.columns.values]
    grouped = grouped.reset_index()
    
    # Rename columns for clarity
    grouped = grouped.rename(columns={
        'instance_name_count': 'number_of_instances',
        'bin_types_used_mean': 'avg_bin_types_used',
        'bin_types_used_std': 'std_bin_types_used',
        'bin_types_used_min': 'min_bin_types_used',
        'bin_types_used_max': 'max_bin_types_used',
        'dominant_supplier_share_percent_mean': 'avg_dominant_supplier_share',
        'dominant_supplier_share_percent_std': 'std_dominant_supplier_share',
        'dominant_supplier_share_percent_min': 'min_dominant_supplier_share',
        'dominant_supplier_share_percent_max': 'max_dominant_supplier_share',
        'dominant_bin_type_share_percent_mean': 'avg_dominant_bin_type_share',
        'dominant_bin_type_share_percent_std': 'std_dominant_bin_type_share',
        'dominant_bin_type_share_percent_min': 'min_dominant_bin_type_share',
        'dominant_bin_type_share_percent_max': 'max_dominant_bin_type_share',
        'dominant_bin_single_volume_mean': 'avg_dominant_bin_single_volume',
        'dominant_bin_single_volume_std': 'std_dominant_bin_single_volume',
        'dominant_bin_single_volume_min': 'min_dominant_bin_single_volume',
        'dominant_bin_single_volume_max': 'max_dominant_bin_single_volume',
        'items_in_dominant_bin_percent_mean': 'avg_items_in_dominant_bin',
        'items_in_dominant_bin_percent_std': 'std_items_in_dominant_bin',
        'items_in_dominant_bin_percent_min': 'min_items_in_dominant_bin',
        'items_in_dominant_bin_percent_max': 'max_items_in_dominant_bin',
        # NEW: Enhanced KPIs column names
        'supplier_concentration_index_mean': 'avg_supplier_concentration',
        'supplier_concentration_index_std': 'std_supplier_concentration',
        'supplier_concentration_index_min': 'min_supplier_concentration',
        'supplier_concentration_index_max': 'max_supplier_concentration',
        'bin_type_concentration_index_mean': 'avg_bin_type_concentration',
        'bin_type_concentration_index_std': 'std_bin_type_concentration',
        'bin_type_concentration_index_min': 'min_bin_type_concentration',
        'bin_type_concentration_index_max': 'max_bin_type_concentration',
        'dominant_bin_utilization_rate_mean': 'avg_dominant_bin_utilization',
        'dominant_bin_utilization_rate_std': 'std_dominant_bin_utilization',
        'dominant_bin_utilization_rate_min': 'min_dominant_bin_utilization',
        'dominant_bin_utilization_rate_max': 'max_dominant_bin_utilization',
        'dominant_supplier_bin_variety_mean': 'avg_dominant_supplier_bin_variety',
        'dominant_supplier_bin_variety_std': 'std_dominant_supplier_bin_variety',
        'dominant_supplier_bin_variety_min': 'min_dominant_supplier_bin_variety',
        'dominant_supplier_bin_variety_max': 'max_dominant_supplier_bin_variety',
        'dominant_supplier_provides_dominant_bin_mean': 'pct_dominant_supplier_provides_dominant_bin',
        'total_volume_booked_mean': 'avg_total_volume_booked',
        'total_volume_booked_std': 'std_total_volume_booked',
        'total_items_packed_mean': 'avg_total_items_packed',
        'total_items_packed_std': 'std_total_items_packed'
    })
    
    return grouped

def analyze_dominant_bin_types(instance_kpis: pd.DataFrame) -> pd.DataFrame:
    """
    Analyze frequency of dominant bin types across instances
    """
    if instance_kpis.empty:
        return pd.DataFrame()
    
    dominant_bin_analysis = instance_kpis.groupby('dominant_bin_type').agg({
        'instance_name': 'count',
        'dominant_bin_type_share_percent': 'mean',
        'dominant_bin_single_volume': 'mean',
        'items_in_dominant_bin_percent': 'mean',
        'dominant_bin_utilization_rate': 'mean',  # NEW
        'bin_type_concentration_index': 'mean'    # NEW
    }).round(3)
    
    dominant_bin_analysis = dominant_bin_analysis.rename(columns={
        'instance_name': 'frequency',
        'dominant_bin_type_share_percent': 'avg_share_percent',
        'dominant_bin_single_volume': 'avg_single_volume',
        'items_in_dominant_bin_percent': 'avg_items_percent',
        'dominant_bin_utilization_rate': 'avg_utilization_rate',  # NEW
        'bin_type_concentration_index': 'avg_concentration'       # NEW
    })
    
    dominant_bin_analysis['frequency_percent'] = (dominant_bin_analysis['frequency'] / len(instance_kpis) * 100).round(1)
    dominant_bin_analysis = dominant_bin_analysis.sort_values('frequency', ascending=False)
    
    return dominant_bin_analysis

def main():
    """
    Main function to compute and export KPIs
    """
    print("=== COMPUTING ENHANCED KPIs ===")
    
    # Process all instances
    file_pattern = "cpssp_*.sspsol"  # Adjust pattern as needed
    instance_kpis = process_all_instances(file_pattern)
    
    if instance_kpis.empty:
        print("No files processed. Check your file pattern.")
        return
    
    # Generate aggregated KPIs
    aggregated_kpis = generate_aggregated_kpis(instance_kpis)
    
    # Analyze dominant bin types
    dominant_bin_analysis = analyze_dominant_bin_types(instance_kpis)
    
    # Save instance-level KPIs
    instance_kpis.to_csv('instance_level_kpis.csv', index=False)
    print(f"Instance-level KPIs saved to 'instance_level_kpis.csv'")
    print(f"Processed {len(instance_kpis)} instances")
    
    # Save aggregated KPIs
    if not aggregated_kpis.empty:
        aggregated_kpis.to_csv('aggregated_kpis_by_config.csv', index=False)
        print("Aggregated KPIs saved to 'aggregated_kpis_by_config.csv'")
        
        print("\n=== AGGREGATED KPIs SUMMARY ===")
        print(aggregated_kpis.to_string(index=False))
    
    # Save dominant bin type analysis
    if not dominant_bin_analysis.empty:
        dominant_bin_analysis.to_csv('dominant_bin_type_analysis.csv')
        print("\nDominant bin type analysis saved to 'dominant_bin_type_analysis.csv'")
        print("\n=== DOMINANT BIN TYPE FREQUENCY ===")
        print(dominant_bin_analysis.to_string())
    
    # Print summary statistics
    print(f"\n=== OVERALL SUMMARY ===")
    print(f"Average bin types used: {instance_kpis['bin_types_used'].mean():.2f}")
    print(f"Average dominant supplier share: {instance_kpis['dominant_supplier_share_percent'].mean():.1f}%")
    print(f"Average dominant bin type share: {instance_kpis['dominant_bin_type_share_percent'].mean():.1f}%")
    print(f"Average dominant bin single volume: {instance_kpis['dominant_bin_single_volume'].mean():.1f}")
    print(f"Average items in dominant bin type: {instance_kpis['items_in_dominant_bin_percent'].mean():.1f}%")
    print(f"Average supplier concentration: {instance_kpis['supplier_concentration_index'].mean():.3f}")
    print(f"Average bin type concentration: {instance_kpis['bin_type_concentration_index'].mean():.3f}")
    print(f"Average dominant bin utilization rate: {instance_kpis['dominant_bin_utilization_rate'].mean():.3f}")
    print(f"Average dominant supplier bin variety: {instance_kpis['dominant_supplier_bin_variety'].mean():.2f}")
    print(f"Dominant supplier provides dominant bin type: {instance_kpis['dominant_supplier_provides_dominant_bin'].mean():.1%}")
    
    # Check if solutions always pack all items
    all_items_packed = (instance_kpis['total_items_packed'] == instance_kpis['nr_items']).all()
    print(f"All items packed in every solution: {all_items_packed}")

if __name__ == "__main__":
    main()
